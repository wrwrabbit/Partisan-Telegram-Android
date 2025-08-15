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

static void ParameterModification(double shift, double ratio, int fs, int f0_length,
                                  int fft_size, double* f0, double** spectrogram) {
    for (int i = 0; i < f0_length; ++i) {
        f0[i] *= shift;
    }

    double* freq_axis1 = new double[fft_size];
    double* freq_axis2 = new double[fft_size];
    double* spectrum1 = new double[fft_size];
    double* spectrum2 = new double[fft_size];

    for (int i = 0; i <= fft_size / 2; ++i) {
        freq_axis1[i] = ratio * i / fft_size * fs;
        freq_axis2[i] = static_cast<double>(i) / fft_size * fs;
    }
    for (int i = 0; i < f0_length; ++i) {
        for (int j = 0; j <= fft_size / 2; ++j) {
            spectrum1[j] = log(spectrogram[i][j]);
        }
        interp1(freq_axis1, spectrum1, fft_size / 2 + 1, freq_axis2, fft_size / 2 + 1, spectrum2);
        for (int j = 0; j <= fft_size / 2; ++j) {
            spectrogram[i][j] = exp(spectrum2[j]);
        }
        if (ratio >= 1.0) {
            continue;
        }
        for (int j = static_cast<int>(fft_size / 2.0 * ratio); j <= fft_size / 2; ++j) {
            spectrogram[i][j] = spectrogram[i][static_cast<int>(fft_size / 2.0 * ratio) - 1];
        }
    }
    delete[] spectrum1;
    delete[] spectrum2;
    delete[] freq_axis1;
    delete[] freq_axis2;
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

static void ShiftFormants(double shift, double ratio, int fs, const float* x_float, int x_length, float* y_float, int* y_length) {
    double* x = new double[x_length];
    FloatArrayToDoubleArray(x_float, x, x_length);

    WorldParameters world_parameters = { 0 };
    world_parameters.fs = fs;
    world_parameters.frame_period = 5.0;

    F0EstimationDio(x, x_length, &world_parameters);
    SpectralEnvelopeEstimation(x, x_length, &world_parameters);
    AperiodicityEstimation(x, x_length, &world_parameters);

    ParameterModification(shift, ratio, fs, world_parameters.f0_length,
                          world_parameters.fft_size, world_parameters.f0,
                          world_parameters.spectrogram);

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

extern "C" JNIEXPORT jint Java_org_telegram_messenger_partisan_voicechange_WorldUtils_shiftFormants(JNIEnv *env, jclass clazz, jdouble shift, jdouble ratio, jint fs, jfloatArray x, jint x_length, jfloatArray y) {
    jfloat* xTmp = env->GetFloatArrayElements(x, nullptr);
    jfloat* yTmp = env->GetFloatArrayElements(y, nullptr);
    int yLength;
    ShiftFormants(shift, ratio, fs, xTmp, x_length, yTmp, &yLength);
    return yLength;
}
