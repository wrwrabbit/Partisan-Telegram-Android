#include <cmath>

#include "c_utils.h"
#include "world/world/dio.h"
#include "world/world/stonemask.h"
#include "world/world/harvest.h"
#include "world/world/cheaptrick.h"
#include "world/world/d4c.h"
#include "world/world/matlabfunctions.h"
#include "world/world/synthesisrealtime.h"
#include "world/world/synthesis.h"

typedef struct {
    double frame_period;
    int fs;

    double *f0;
    double *time_axis;
    int f0_length;

    double **spectrogram;
    double **aperiodicity;
    int fft_size;
} WorldParameters;

static DioOption CreateDioOptions(double frame_period) {
    DioOption option = {0};
    InitializeDioOption(&option);

    option.frame_period = frame_period;
    option.speed = 1;
    option.f0_floor = 40.0;
    option.allowed_range = 0.1;
    return option;
}

static void F0EstimationDio(double *x, int x_length,
                     WorldParameters *world_parameters) {
    DioOption option = CreateDioOptions(world_parameters->frame_period);

    world_parameters->f0_length = GetSamplesForDIO(world_parameters->fs, x_length, world_parameters->frame_period);
    world_parameters->f0 = new double[world_parameters->f0_length];
    world_parameters->time_axis = new double[world_parameters->f0_length];

    double *refined_f0 = new double[world_parameters->f0_length];

    Dio(x, x_length, world_parameters->fs, &option, world_parameters->time_axis, world_parameters->f0);
    StoneMask(x, x_length, world_parameters->fs, world_parameters->time_axis, world_parameters->f0, world_parameters->f0_length, refined_f0);

    for (int i = 0; i < world_parameters->f0_length; ++i) {
        world_parameters->f0[i] = refined_f0[i];
    }

    delete[] refined_f0;
}

static void F0EstimationHarvest(double *x, int x_length,
                         WorldParameters *world_parameters) {
    HarvestOption option = { 0 };
    InitializeHarvestOption(&option);

    option.frame_period = world_parameters->frame_period;

    option.f0_floor = 40.0;

    world_parameters->f0_length = GetSamplesForHarvest(world_parameters->fs,
                                                       x_length, world_parameters->frame_period);
    world_parameters->f0 = new double[world_parameters->f0_length];
    world_parameters->time_axis = new double[world_parameters->f0_length];

    Harvest(x, x_length, world_parameters->fs, &option,
            world_parameters->time_axis, world_parameters->f0);
}

static void SpectralEnvelopeEstimation(double *x, int x_length,
                                WorldParameters *world_parameters) {
    CheapTrickOption option = {0};
    InitializeCheapTrickOption(world_parameters->fs, &option);
    option.f0_floor = 71.0;
    option.fft_size = GetFFTSizeForCheapTrick(world_parameters->fs, &option);

    world_parameters->fft_size = option.fft_size;
    world_parameters->spectrogram = new double *[world_parameters->f0_length];
    for (int i = 0; i < world_parameters->f0_length; ++i) {
        world_parameters->spectrogram[i] = new double[world_parameters->fft_size / 2 + 1];
    }

    CheapTrick(x, x_length, world_parameters->fs, world_parameters->time_axis,
               world_parameters->f0, world_parameters->f0_length, &option,
               world_parameters->spectrogram);
}

static void AperiodicityEstimation(double *x, int x_length,
                            WorldParameters *world_parameters) {
    D4COption option = {0};
    InitializeD4COption(&option);

    option.threshold = 0.85;

    world_parameters->aperiodicity = new double *[world_parameters->f0_length];
    for (int i = 0; i < world_parameters->f0_length; ++i) {
        world_parameters->aperiodicity[i] = new double[world_parameters->fft_size / 2 + 1];
    }

    D4C(x, x_length, world_parameters->fs, world_parameters->time_axis,
        world_parameters->f0, world_parameters->f0_length,
        world_parameters->fft_size, &option, world_parameters->aperiodicity);
}

void BreakSounds(double** spectrogram, int fft_size, double* f0, int fs, int f0_length,
                  int bad_s_threshold, int bad_s_cutoff,
                  int bad_sh_min_threshold, int bad_sh_max_threshold, int bad_sh_cutoff)
{
    double* freq_axis = new double[fft_size];
    for (int i = 0; i <= fft_size / 2; ++i) {
        freq_axis[i] = static_cast<double>(i) / fft_size * fs;
    }

    for (int i = 0; i < f0_length; ++i) {
        double full_sum = 0.0;
        double magnitudes_sum = 0.0;
        for (int j = 0; j <= fft_size / 2; ++j) {
            double current_abs_magnitude = std::abs(spectrogram[i][j]);
            magnitudes_sum += current_abs_magnitude;
            full_sum += current_abs_magnitude * freq_axis[j];
        }

        double centroid = magnitudes_sum > 0 ? full_sum / magnitudes_sum : 0.0;
        if (bad_s_cutoff > 0 && centroid > bad_s_threshold) {
            for (int j = fft_size / 2; j > 0 && freq_axis[j] > bad_s_cutoff; j--) {
                spectrogram[i][j] *= 1E-7;
            }
        } else if (bad_sh_cutoff > 0 && bad_sh_min_threshold < centroid && centroid < bad_sh_max_threshold) {
            for (int j = 0; j < fft_size / 2 && freq_axis[j] < bad_sh_cutoff; j++) {
                spectrogram[i][j] = 1E-7;
            }
        }
    }
    delete[] freq_axis;
}

struct FormantRatioParameters {
    double ratio_from;
    double ratio_to;

    bool isDefault() {
        return std::abs(ratio_from - 1.0) < 1E-6 && std::abs(ratio_to - 1.0) < 1E-6;
    }
};

struct FormantParameters {
    double shift_from;
    double shift_to;
    FormantRatioParameters lowFormantRatio;
    FormantRatioParameters midFormantRatio;
    FormantRatioParameters highFormantRatio;

    bool isDefault() {
        return std::abs(shift_from - 1.0) < 1E-6 && std::abs(shift_to - 1.0) < 1E-6
               && lowFormantRatio.isDefault() && midFormantRatio.isDefault() && highFormantRatio.isDefault();
    }
};

class FormantShifter {
public:
    FormantShifter(WorldParameters& worldParameters, FormantParameters& formantParameters);
    ~FormantShifter();
    void performShifting();

private:
    double** createSpectrogramMatrix();
    void deleteSpectrogramMatrix(double** spectrogram);

    void shiftF0();
    void shiftFormants(FormantRatioParameters formantRatio, int i, int fromFrequencyIndex, int toFrequencyIndex);
    void saveSpectrogram();

    WorldParameters& worldParameters;
    FormantParameters& formantParameters;

    double* freq_axis1;
    double* freq_axis2;
    double* spectrum1;
    double* spectrum2;
    double* tempSpectrum;
    double** resultSpectrogram;
};

FormantShifter::FormantShifter(WorldParameters& worldParameters, FormantParameters& formantParameters)
        : worldParameters(worldParameters)
        , formantParameters(formantParameters)
{
    freq_axis1 = new double[worldParameters.fft_size];
    freq_axis2 = new double[worldParameters.fft_size];
    spectrum1 = new double[worldParameters.fft_size];
    spectrum2 = new double[worldParameters.fft_size];

    tempSpectrum = new double[worldParameters.fft_size];
    resultSpectrogram = createSpectrogramMatrix();
}

FormantShifter::~FormantShifter() {
    delete[] freq_axis1;
    delete[] freq_axis2;
    delete[] spectrum1;
    delete[] spectrum2;
    delete[] tempSpectrum;
    deleteSpectrogramMatrix(resultSpectrogram);
}

void FormantShifter::performShifting() {
    shiftF0();
    int midFrequencyMinIndex = (int)round(1000.0 * worldParameters.fft_size / worldParameters.fs);
    int highFrequencyMinIndex = (int)round(4000.0 * worldParameters.fft_size / worldParameters.fs);
    for (int i = 0; i < worldParameters.f0_length; ++i) {
        for (int j = 0; j <= worldParameters.fft_size / 2; ++j) {
            spectrum1[j] = log(worldParameters.spectrogram[i][j]);
        }
        shiftFormants(formantParameters.lowFormantRatio, i, 0.0, midFrequencyMinIndex);
        shiftFormants(formantParameters.midFormantRatio, i, midFrequencyMinIndex, highFrequencyMinIndex);
        shiftFormants(formantParameters.highFormantRatio, i, highFrequencyMinIndex, worldParameters.fft_size/2 + 1);
    }
    saveSpectrogram();
}

void FormantShifter::shiftF0() {
    for (int i = 0; i < worldParameters.f0_length; ++i) {
        double current_shift = formantParameters.shift_from + (formantParameters.shift_to - formantParameters.shift_from) * ((double)i / (double)worldParameters.f0_length);
        worldParameters.f0[i] *= current_shift;
    }
}

double** FormantShifter::createSpectrogramMatrix() {
    double** spectrogram = new double* [worldParameters.f0_length];
    for (int i = 0; i < worldParameters.f0_length; ++i) {
        spectrogram[i] = new double[worldParameters.fft_size / 2 + 1];
    }
    return spectrogram;
}

void FormantShifter::deleteSpectrogramMatrix(double** spectrogram) {
    for (int i = 0; i < worldParameters.f0_length; ++i) {
        delete[] spectrogram[i];
    }
    delete[] spectrogram;
}

void FormantShifter::shiftFormants(FormantRatioParameters formantRatio, int frameIndex, int fromFrequencyIndex, int toFrequencyIndex) {
    double currentRatio = formantRatio.ratio_from + (formantRatio.ratio_to - formantRatio.ratio_from) * ((double)frameIndex / (double)worldParameters.f0_length);
    for (int j = 0; j <= worldParameters.fft_size / 2; ++j) {
        freq_axis1[j] = currentRatio * j / worldParameters.fft_size * worldParameters.fs;
        freq_axis2[j] = static_cast<double>(j) / worldParameters.fft_size * worldParameters.fs;
    }

    interp1(freq_axis1, spectrum1, worldParameters.fft_size / 2 + 1, freq_axis2, worldParameters.fft_size / 2 + 1, spectrum2);

    for (int j = fromFrequencyIndex; j < toFrequencyIndex; ++j)
        tempSpectrum[j] = exp(spectrum2[j]);

    if (currentRatio < 1.0) {
        for (int j = static_cast<int>(worldParameters.fft_size / 2.0 * currentRatio); j <= worldParameters.fft_size / 2; ++j) {
            tempSpectrum[j] = worldParameters.spectrogram[frameIndex][static_cast<int>(worldParameters.fft_size / 2.0 * currentRatio) - 1];
        }
    }

    for (int j = fromFrequencyIndex; j < toFrequencyIndex; ++j) {
        resultSpectrogram[frameIndex][j] = tempSpectrum[j];
    }
}

void FormantShifter::saveSpectrogram() {
    for (int i = 0; i < worldParameters.f0_length; ++i) {
        for (int j = 0; j < worldParameters.fft_size / 2; ++j) {
            worldParameters.spectrogram[i][j] = resultSpectrogram[i][j];
        }
    }
}

static void WaveformSynthesis(WorldParameters *world_parameters, int fs,
                       int y_length, double *y) {
    Synthesis(world_parameters->f0, world_parameters->f0_length,
              world_parameters->spectrogram, world_parameters->aperiodicity,
              world_parameters->fft_size, world_parameters->frame_period, fs,
              y_length, y);
}

static void DestroyMemory(WorldParameters *world_parameters) {
    delete[] world_parameters->time_axis;
    delete[] world_parameters->f0;
    for (int i = 0; i < world_parameters->f0_length; ++i) {
        delete[] world_parameters->spectrogram[i];
        delete[] world_parameters->aperiodicity[i];
    }
    delete[] world_parameters->spectrogram;
    delete[] world_parameters->aperiodicity;
}

static void FloatArrayToDoubleArray(const float* srcArray, double* destArray, int length) {
    for (int i = 0; i < length; i++) {
        destArray[i] = (double)srcArray[i];
    }
}

static void DoubleArrayToFloatArray(const double* srcArray, float* destArray, int length) {
    for (int i = 0; i < length; i++) {
        destArray[i] = (float)srcArray[i];
    }
}

static int CalculateYLength(WorldParameters* world_parameters) {
    return static_cast<int>((world_parameters->f0_length - 1) *
                            world_parameters->frame_period / 1000.0 * world_parameters->fs) + 1;
}

static void ClipAudioData(double *y, int y_length) {
    for (int i = 0; i < y_length; ++i) {
        if (y[i] > 1.0 || y[i] < -1.0) {
            if (y[i] > 1.0) {
                y[i] = 1.0;
            } else {
                y[i] = -1.0;
            }
        }
    }
}

static void ChangeVoice(
        double shift_from, double shift_to,
        double low_ratio_from, double low_ratio_to,
        double mid_ratio_from, double mid_ratio_to,
        double high_ratio_from, double high_ratio_to,
        int fs, const float* x_float, int x_length, float* y_float, int* y_length, int harvest,
        int bad_s_threshold, int bad_s_cutoff,
        int bad_sh_min_threshold, int bad_sh_max_threshold, int bad_sh_cutoff)
{
    double* x = new double[x_length];
    FloatArrayToDoubleArray(x_float, x, x_length);

    WorldParameters world_parameters = { 0 };
    world_parameters.fs = fs;
    world_parameters.frame_period = 5.0;

    if (harvest) {
        F0EstimationHarvest(x, x_length, &world_parameters);
    } else {
        F0EstimationDio(x, x_length, &world_parameters);
    }
    SpectralEnvelopeEstimation(x, x_length, &world_parameters);
    AperiodicityEstimation(x, x_length, &world_parameters);

    if (bad_s_cutoff > 0 || bad_sh_cutoff > 0) {
        BreakSounds(world_parameters.spectrogram, world_parameters.fft_size, world_parameters.f0, fs, world_parameters.f0_length,
                     bad_s_threshold, bad_s_cutoff,
                     bad_sh_min_threshold, bad_sh_max_threshold, bad_sh_cutoff);
    }

    FormantParameters formantParameters{
            shift_from,
            shift_to,
            {low_ratio_from, low_ratio_to},
            {mid_ratio_from, mid_ratio_to},
            {high_ratio_from, high_ratio_to}
    };
    if (!formantParameters.isDefault()) {
        FormantShifter(world_parameters, formantParameters).performShifting();
    }

    *y_length = CalculateYLength(&world_parameters);
    double* y = new double[*y_length];
    for (int i = 0; i < *y_length; ++i) {
        y[i] = 0.0;
    }
    WaveformSynthesis(&world_parameters, fs, *y_length, y);

    ClipAudioData(y, *y_length);
    DoubleArrayToFloatArray(y, y_float, *y_length);

    DestroyMemory(&world_parameters);
    delete[] x;
    delete[] y;
}

extern "C" JNIEXPORT jint Java_org_telegram_messenger_partisan_voicechange_WorldVocoder_changeVoice(JNIEnv *env, jclass clazz,
                                                    jdouble shift_from, jdouble shift_to,
                                                    jdouble low_ratio_from, jdouble low_ratio_to,
                                                    jdouble mid_ratio_from, jdouble mid_ratio_to,
                                                    jdouble high_ratio_from, jdouble high_ratio_to,
                                                    jint fs, jfloatArray x, jint x_length, jfloatArray y, jint harvest,
                                                    jint bad_s_threshold, jint bad_s_cutoff,
                                                    jint bad_sh_min_threshold, jint bad_sh_max_threshold, jint bad_sh_cutoff) {
    jfloat* xTmp = env->GetFloatArrayElements(x, nullptr);
    jfloat* yTmp = env->GetFloatArrayElements(y, nullptr);
    int yLength;
    ChangeVoice(shift_from, shift_to,
                low_ratio_from, low_ratio_to,
                mid_ratio_from, mid_ratio_to,
                high_ratio_from, high_ratio_to,
                fs, xTmp, x_length, yTmp, &yLength, harvest,
                bad_s_threshold, bad_s_cutoff,
                bad_sh_min_threshold, bad_sh_max_threshold, bad_sh_cutoff);
    env->ReleaseFloatArrayElements(x, xTmp, JNI_ABORT);
    env->ReleaseFloatArrayElements(y, yTmp, 0);
    return yLength;
}
