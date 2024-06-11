package org.telegram.messenger.partisan.verification;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VerificationRepository {
    public static final int TYPE_VERIFIED = 1;
    public static final int TYPE_SCAM = 2;
    public static final int TYPE_FAKE = 3;
    private boolean repositoryLoaded;
    private boolean loadedWithErrors;
    private List<VerificationStorage> storages = new ArrayList<>();
    private final Map<Long, Integer> cacheTypeByChatId = new HashMap<>();
    private static VerificationRepository instance;

    public static synchronized VerificationRepository getInstance() {
        if (instance == null) {
            instance = new VerificationRepository();
        }
        return instance;
    }

    private static class StoragesWrapper {
        public List<VerificationStorage> verificationStorages;
        public StoragesWrapper(List<VerificationStorage> verificationStorages) {
            this.verificationStorages = verificationStorages;
        }
        public StoragesWrapper() {}
    }

    public synchronized void loadRepository() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("verified", Context.MODE_PRIVATE);
            boolean repositoryFilled = preferences.contains("storages");
            if (repositoryFilled) {
                storages = SharedConfig.fromJson(preferences.getString("storages", null), StoragesWrapper.class).verificationStorages;
                for (VerificationStorage storage : storages) {
                    storage.migrate();
                }
                updateCache();
            } else {
                fillRepository();
            }
        } catch (Exception ignore) {
            loadedWithErrors = true;
        }
    }

    public void saveRepository() {
        if (!loadedWithErrors) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("verified", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("storages", SharedConfig.toJson(new StoragesWrapper(storages)));
                editor.apply();
                updateCache();
            } catch (JsonProcessingException ignore) {
            }
        }
    }

    private void fillRepository() {
        try {
            VerificationStorage storage;
            if (BuildVars.isAlphaApp()) {
                storage = new VerificationStorage("Cyber Partisans Test", "ptg_verification_alpha", -2013847836);
            } else {
                storage = new VerificationStorage("Cyber Partisans", "ptgsymb", -2064662503);
            }
            storages.add(storage);

            storage.chats.add(new VerificationChatInfo(989056630L, "cpartisans_bot", 1));
            storage.chats.add(new VerificationChatInfo(5106491425L, "cpartisans_urgent_bot", 1));
            storage.chats.add(new VerificationChatInfo(5217087258L, "partisan_qa_bot", 1));
            storage.chats.add(new VerificationChatInfo(5259637648L, "cpartisans_join_bot", 1));
            storage.chats.add(new VerificationChatInfo(2066143564L, "partisan_telegram_bot", 1));
            storage.chats.add(new VerificationChatInfo(5817399651L, "facement_bot", 1));
            storage.chats.add(new VerificationChatInfo(1477761243L, "Busliniybot", 1));
            storage.chats.add(new VerificationChatInfo(1680003670L, "dns_coord_bot", 1));
            storage.chats.add(new VerificationChatInfo(5197056745L, "TGBel_bot", 1));
            storage.chats.add(new VerificationChatInfo(5269881457L, "BelarusAndUkraineBot", 1));
            storage.chats.add(new VerificationChatInfo(5248690359L, "occupant_info_bot", 1));
            storage.chats.add(new VerificationChatInfo(1764081723L, "bz_support_bot", 1));
            storage.chats.add(new VerificationChatInfo(1826798139L, "ReturnWhatStolen_bot", 1));
            storage.chats.add(new VerificationChatInfo(1203525499L, "belarusy_zarubezhja_bot", 1));
            storage.chats.add(new VerificationChatInfo(1201956582L, "zerkalo_editor", 1));
            storage.chats.add(new VerificationChatInfo(781931059L, "euroradio_minsk", 1));
            storage.chats.add(new VerificationChatInfo(6153646860L, "info_charter97", 1));
            storage.chats.add(new VerificationChatInfo(783723940L, "SvabodaBelarus", 1));
            storage.chats.add(new VerificationChatInfo(733628894L, "Motolko_bot", 1));
            storage.chats.add(new VerificationChatInfo(5233981354L, "HajunBYbot", 1));
            storage.chats.add(new VerificationChatInfo(1408155238L, "BelsatBot", 1));
            storage.chats.add(new VerificationChatInfo(5088044675L, "basta11_bot", 1));
            storage.chats.add(new VerificationChatInfo(1254883880L, "BGMnews_bot", 1));
            storage.chats.add(new VerificationChatInfo(1807622699L, "sanctionswatch_bot", 1));
            storage.chats.add(new VerificationChatInfo(1235073753L, "MKBelbot", 1));
            storage.chats.add(new VerificationChatInfo(1610868721L, "real_belarus_bot", 1));
            storage.chats.add(new VerificationChatInfo(1270329713L, "strana_official_bot", 1));
            storage.chats.add(new VerificationChatInfo(1325073348L, "KYKYmediabot", 1));
            storage.chats.add(new VerificationChatInfo(1326631129L, "belteanewsbot", 1));
            storage.chats.add(new VerificationChatInfo(1578684412L, "nick_and_mikeBot", 1));
            storage.chats.add(new VerificationChatInfo(1306844446L, "fraw_marta_bot", 1));
            storage.chats.add(new VerificationChatInfo(1448750362L, "dze_bot", 1));
            storage.chats.add(new VerificationChatInfo(1314492187L, "menskrazam_bot", 1));
            storage.chats.add(new VerificationChatInfo(1207923033L, "TheVillageBelarusBot", 1));
            storage.chats.add(new VerificationChatInfo(5611596810L, "BOR_pochta_bot", 1));
            storage.chats.add(new VerificationChatInfo(1445915448L, "BLsupport_bot", 1));
            storage.chats.add(new VerificationChatInfo(1632896478L, "zamkadombot", 1));
            storage.chats.add(new VerificationChatInfo(1835507118L, "ByProsvetVolunteerBot", 1));
            storage.chats.add(new VerificationChatInfo(1512107110L, "by_prosvet_feedback_bot", 1));
            storage.chats.add(new VerificationChatInfo(1263764071L, "fuckshtok", 1));
            storage.chats.add(new VerificationChatInfo(1428483199L, "Dmbolk_bot", 1));
            storage.chats.add(new VerificationChatInfo(1464870731L, "belhalat_bot", 1));
            storage.chats.add(new VerificationChatInfo(1271955412L, "ChatHonest_bot", 1));
            storage.chats.add(new VerificationChatInfo(688209485L, "viasna_bot", 1));
            storage.chats.add(new VerificationChatInfo(1635921527L, "stachka_by_bot", 1));
            storage.chats.add(new VerificationChatInfo(1715255901L, "ruh_connect_bot", 1));
            storage.chats.add(new VerificationChatInfo(5394894107L, "nau_by_bot", 1));
            storage.chats.add(new VerificationChatInfo(5054426164L, "kpd_by_bot", 1));
            storage.chats.add(new VerificationChatInfo(5010895223L, "hiveguide_bot", 1));
            storage.chats.add(new VerificationChatInfo(1383135185L, "belzhd_bot", 1));
            storage.chats.add(new VerificationChatInfo(1265159441L, "belzhd_editor", 1));
            storage.chats.add(new VerificationChatInfo(1855819538L, "plan_peramoga_bot", 1));
            storage.chats.add(new VerificationChatInfo(5955987812L, "bypol_chats_bot", 1));
            storage.chats.add(new VerificationChatInfo(1468118581L, "AskOffice_Bot", 1));
            storage.chats.add(new VerificationChatInfo(1313749067L, "FourOneOne4111", 1));
            storage.chats.add(new VerificationChatInfo(5263169835L, "EXOMON_support_bot", 1));
            storage.chats.add(new VerificationChatInfo(2009270454L, "balaganoff_bot", 1));
            storage.chats.add(new VerificationChatInfo(5048219469L, "mail_by", 1));
            storage.chats.add(new VerificationChatInfo(6007283902L, "Rezbat_bot", 1));
            storage.chats.add(new VerificationChatInfo(5737683598L, "pkk_reserve_bot", 1));
            storage.chats.add(new VerificationChatInfo(6508533990L, "belpolinfobot", 1));
            storage.chats.add(new VerificationChatInfo(6271362579L, "RuchBelNac_BOT", 1));
            storage.chats.add(new VerificationChatInfo(5937370959L, "rushennie_bot", 1));
            storage.chats.add(new VerificationChatInfo(5494132715L, "vb_contact_bot", 1));
            storage.chats.add(new VerificationChatInfo(863584518L, "worldprotest_bot", 1));
            storage.chats.add(new VerificationChatInfo(1647034311L, "vybor_by_bot", 1));
            storage.chats.add(new VerificationChatInfo(5507948945L, "spasisebyabot", 1));
            storage.chats.add(new VerificationChatInfo(5884078727L, "Rus_ni_peace_da_bot", 1));
            storage.chats.add(new VerificationChatInfo(5829538792L, "Yug_mopedi_bot", 1));
            storage.chats.add(new VerificationChatInfo(1944603193L, "BlackMap_bot", 1));
            storage.chats.add(new VerificationChatInfo(5927501949L, "mediazonaaby", 1));
            storage.chats.add(new VerificationChatInfo(6214140942L, "suviaz_hl", 1));
            storage.chats.add(new VerificationChatInfo(1768396905L, "CyberBeaverBot", 1));
            storage.chats.add(new VerificationChatInfo(1539605834L, "cyberpartisan_bot", 1));
            storage.chats.add(new VerificationChatInfo(1558366823L, "dissidentby_bot", 1));
            storage.chats.add(new VerificationChatInfo(983411607L, "devby_insight_bot", 1));
            storage.chats.add(new VerificationChatInfo(1091595349L, "Malanka_inbox_bot", 1));
            storage.chats.add(new VerificationChatInfo(6616396639L, "ap_narod_bot", 1));
            storage.chats.add(new VerificationChatInfo(5280919337L, "Suprativ_support_bot", 1));
            storage.chats.add(new VerificationChatInfo(6092224989L, "FindMessagesBot", 1));
            storage.chats.add(new VerificationChatInfo(5129059224L, "bysol_evacuation_bot", 1));
            storage.chats.add(new VerificationChatInfo(1125659785L, "golosby_bot", 1));
            storage.chats.add(new VerificationChatInfo(5001919716L, "mostmedia_bot", 1));
            storage.chats.add(new VerificationChatInfo(728075370L, "CityDogBot", 1));
            storage.chats.add(new VerificationChatInfo(6536668537L, "Intelligenceby_bot", 1));
            storage.chats.add(new VerificationChatInfo(1469327702L, "NovajaZiamla_bot", 1));
            storage.chats.add(new VerificationChatInfo(2132540208L, "AF_BY_bot", 1));
            storage.chats.add(new VerificationChatInfo(1572171994L, "PanHistoryjaBot", 1));
            storage.chats.add(new VerificationChatInfo(406856131L, "NicolaiKhalezin", 1));
            storage.chats.add(new VerificationChatInfo(1753785715L, "MAYDAYhelp", 1));
            storage.chats.add(new VerificationChatInfo(1693285279L, "new_grodno_bot", 1));
            storage.chats.add(new VerificationChatInfo(6575965275L, "PusovBot", 1));
            storage.chats.add(new VerificationChatInfo(1669406514L, "BudzmaSuviaz_Bot", 1));
            storage.chats.add(new VerificationChatInfo(1451640448L, "spiskov_net_bot", 1));
            storage.chats.add(new VerificationChatInfo(1497187972L, "listovki97_bot", 1));
            storage.chats.add(new VerificationChatInfo(960018259L, "ByTribunaComBot", 1));
            storage.chats.add(new VerificationChatInfo(1984834353L, "youtube_by_bot", 1));
            storage.chats.add(new VerificationChatInfo(5635638840L, "terbat_bot", 1));
            storage.chats.add(new VerificationChatInfo(1734398694L, "dns_feedback_bot", 1));
            storage.chats.add(new VerificationChatInfo(1451093794L, "motolko_NBR_bot", 1));
            storage.chats.add(new VerificationChatInfo(1099309671L, "dzechat_bot", 1));

            storage.chats.add(new VerificationChatInfo(2059952039L, "cpartisan_sanonbot", 2));
            storage.chats.add(new VerificationChatInfo(2007785891L, "cpartisans_anon_bot", 2));
            storage.chats.add(new VerificationChatInfo(1153790653L, "Bypoll", 2));
            storage.chats.add(new VerificationChatInfo(1754069446L, "predateliby", 2));

            storage.chats.add(new VerificationChatInfo(5735310739L, "face_menty_bot", 3));
            storage.chats.add(new VerificationChatInfo(5276622916L, "TG_Bel_bot", 3));
            storage.chats.add(new VerificationChatInfo(5617305774L, "occupint_info_bot", 3));
            storage.chats.add(new VerificationChatInfo(5681177489L, "kdp_by_bot", 3));
            storage.chats.add(new VerificationChatInfo(5410932720L, "pian_peiramoga_bot", 3));
            storage.chats.add(new VerificationChatInfo(1839964132L, "plan_pieramoga_bot", 3));
            storage.chats.add(new VerificationChatInfo(5716598480L, "belzdh_bot", 3));
            storage.chats.add(new VerificationChatInfo(1284911026L, "tutbay_bot", 3));
            storage.chats.add(new VerificationChatInfo(5626034674L, "Matolko_bot", 3));
            storage.chats.add(new VerificationChatInfo(6058124728L, "motolkobot", 3));
            storage.chats.add(new VerificationChatInfo(5747297986L, "Motolkohelps_bot", 3));
            storage.chats.add(new VerificationChatInfo(6035117215L, "motolko_news_bot", 3));
            storage.chats.add(new VerificationChatInfo(5740485675L, "MKBbelbot", 3));
            storage.chats.add(new VerificationChatInfo(5028668462L, "cpartisan_bot", 3));
            storage.chats.add(new VerificationChatInfo(5127654526L, "ByProsvetVolunteer_Bot", 3));
            storage.chats.add(new VerificationChatInfo(1854185893L, "Busliniy_bot", 3));
            storage.chats.add(new VerificationChatInfo(6050276725L, "buslinybot", 3));
            storage.chats.add(new VerificationChatInfo(6074163432L, "busliny_bot", 3));
            storage.chats.add(new VerificationChatInfo(1786043956L, "dns_cord_bot", 3));
            storage.chats.add(new VerificationChatInfo(5645487088L, "BelarusAndUkrainieBot", 3));
            storage.chats.add(new VerificationChatInfo(6293881487L, "BelarusAndUkraine_Bot", 3));
            storage.chats.add(new VerificationChatInfo(5606443190L, "BelarusAndUkrainBot", 3));
            storage.chats.add(new VerificationChatInfo(5794806573L, "pkk_reservy_bot", 3));
            storage.chats.add(new VerificationChatInfo(5543028382L, "pkk_reserv_bot", 3));
            storage.chats.add(new VerificationChatInfo(5652124632L, "pkk_reserved_bot", 3));
            storage.chats.add(new VerificationChatInfo(5731843616L, "pkk_reserver_bot", 3));
            storage.chats.add(new VerificationChatInfo(1356576596L, "nick_and_mike_bot", 3));
            storage.chats.add(new VerificationChatInfo(5599734900L, "radiosvabodabot", 3));
            storage.chats.add(new VerificationChatInfo(1793463571L, "honestpeople_bot", 3));
            storage.chats.add(new VerificationChatInfo(5017880312L, "honest_peopIe_bot", 3));
            storage.chats.add(new VerificationChatInfo(1599192547L, "strana_svyaz_bot", 3));
            storage.chats.add(new VerificationChatInfo(5661955867L, "Haiun_BYBot", 3));
            storage.chats.add(new VerificationChatInfo(5948492749L, "bypol_proverka_bot", 3));
            storage.chats.add(new VerificationChatInfo(1407287511L, "ByPol_bot", 3));
            storage.chats.add(new VerificationChatInfo(5840583779L, "OSB_By_Pol_bot", 3));
            storage.chats.add(new VerificationChatInfo(5995726427L, "planperamogabot", 3));
            storage.chats.add(new VerificationChatInfo(6239869956L, "plan_peramogabot", 3));
            storage.chats.add(new VerificationChatInfo(1375049419L, "SupolkaBY_bot", 3));
            storage.chats.add(new VerificationChatInfo(403342504L, "FacementBot", 3));
            storage.chats.add(new VerificationChatInfo(1810832442L, "plan_piramoga_bot", 3));
            storage.chats.add(new VerificationChatInfo(1709650512L, "pieramoga_plan_bot", 3));
            storage.chats.add(new VerificationChatInfo(1709527783L, "plan_peramog_bot", 3));
            storage.chats.add(new VerificationChatInfo(1532380090L, "peramoga_plan_bot", 3));
            storage.chats.add(new VerificationChatInfo(6031551222L, "Razbat_bot", 3));
            storage.chats.add(new VerificationChatInfo(1707221120L, "belwariors", 3));
            storage.chats.add(new VerificationChatInfo(1970634134L, "belwarriors_ru", 3));
            storage.chats.add(new VerificationChatInfo(6292242979L, "BelarusAndIUkraineBot", 3));
            storage.chats.add(new VerificationChatInfo(5474204114L, "BelarussianAndUkraineBot", 3));
            storage.chats.add(new VerificationChatInfo(5914511754L, "BelarusAdnUkraineBot", 3));
            storage.chats.add(new VerificationChatInfo(5630096528L, "HajunBYanon_bot", 3));
            storage.chats.add(new VerificationChatInfo(5614673918L, "HajynBYbot", 3));
            storage.chats.add(new VerificationChatInfo(5757560610L, "HajunBEL_bot", 3));
            storage.chats.add(new VerificationChatInfo(5821054654L, "HajunB_bot", 3));
            storage.chats.add(new VerificationChatInfo(5810084409L, "HajunBYnash_bot", 3));
            storage.chats.add(new VerificationChatInfo(5695418211L, "HajunBLR_bot", 3));
            storage.chats.add(new VerificationChatInfo(1254143359L, "motolkohelp_bot", 3));
            storage.chats.add(new VerificationChatInfo(2110427751L, "MotolkohelpBot", 3));
            storage.chats.add(new VerificationChatInfo(5700151833L, "motolko_newsbot", 3));
            storage.chats.add(new VerificationChatInfo(5698230921L, "MotoIko_bot", 3));
            storage.chats.add(new VerificationChatInfo(1631724675L, "nic_and_mike_bot", 3));
            storage.chats.add(new VerificationChatInfo(5047547433L, "kpd_blr_bot", 3));
            storage.chats.add(new VerificationChatInfo(5053420704L, "kpd_b_bot", 3));
            storage.chats.add(new VerificationChatInfo(5779625449L, "RuchbelnacBot", 3));
            storage.chats.add(new VerificationChatInfo(5135746255L, "TGBelbot", 3));
            storage.chats.add(new VerificationChatInfo(1929789849L, "ruschennie", 3));
            storage.chats.add(new VerificationChatInfo(6260569674L, "Rushenniebot", 3));
            storage.chats.add(new VerificationChatInfo(6143884311L, "Paspalitaje_Rusennie_bot", 3));
            storage.chats.add(new VerificationChatInfo(6123656477L, "rushenniecz_bot", 3));
            storage.chats.add(new VerificationChatInfo(5634483218L, "Volnaja_Belaus_bot", 3));
            storage.chats.add(new VerificationChatInfo(5728606679L, "VolnayBelarus_bot", 3));
            storage.chats.add(new VerificationChatInfo(1159302697L, "golosovanie_RF", 3));
            storage.chats.add(new VerificationChatInfo(1766534445L, "insayderyurec", 3));
            storage.chats.add(new VerificationChatInfo(1730025636L, "cpartisans2020", 3));
            storage.chats.add(new VerificationChatInfo(5423658642L, "worldprotest1bot", 3));
            storage.chats.add(new VerificationChatInfo(1400869810L, "io_zerkalo", 3));
            storage.chats.add(new VerificationChatInfo(1261378820L, "TUTBAY", 3));
            storage.chats.add(new VerificationChatInfo(6125366284L, "zerkalo_iorobot", 3));
            storage.chats.add(new VerificationChatInfo(1864083131L, "nexta", 3));
            storage.chats.add(new VerificationChatInfo(1248808496L, "lats_bot", 3));
            storage.chats.add(new VerificationChatInfo(1847224666L, "stsikhanouskaya", 3));
            storage.chats.add(new VerificationChatInfo(1459405938L, "pulpervoi_official", 3));
            storage.chats.add(new VerificationChatInfo(1404319831L, "CabinetST", 3));
            storage.chats.add(new VerificationChatInfo(1260250495L, "naubelarus_bot", 3));
            storage.chats.add(new VerificationChatInfo(1562636546L, "mcbbelarus", 3));
            storage.chats.add(new VerificationChatInfo(1877831257L, "mkbelarys", 3));
            storage.chats.add(new VerificationChatInfo(1956827792L, "ateshua", 3));
            storage.chats.add(new VerificationChatInfo(5763025616L, "generalchereshnyaBot", 3));
            storage.chats.add(new VerificationChatInfo(1972480858L, "gnilayacherexaa", 3));
            storage.chats.add(new VerificationChatInfo(1699191195L, "bahmut_demon", 3));
            storage.chats.add(new VerificationChatInfo(1810978803L, "zhovtastrichka", 3));
            storage.chats.add(new VerificationChatInfo(1175048525L, "crimean_content", 3));
            storage.chats.add(new VerificationChatInfo(6247851779L, "partizany_crimea_bot", 3));
            storage.chats.add(new VerificationChatInfo(6233710456L, "ateshfire_bot", 3));
            storage.chats.add(new VerificationChatInfo(6273841737L, "zhovta_strichka_ua_bot", 3));
            storage.chats.add(new VerificationChatInfo(1524487787L, "hochy_zhyt", 3));
            storage.chats.add(new VerificationChatInfo(5731381213L, "hochu_zhytbot", 3));
            storage.chats.add(new VerificationChatInfo(6057323348L, "krympartizansbot", 3));
            storage.chats.add(new VerificationChatInfo(1812284976L, "gniIayacherexa", 3));
            storage.chats.add(new VerificationChatInfo(5830427793L, "zhovtastrichka_bot", 3));
            storage.chats.add(new VerificationChatInfo(5857031037L, "yellowribbon_uabot", 3));
            storage.chats.add(new VerificationChatInfo(5826729656L, "zhovta_strichkabot", 3));
            storage.chats.add(new VerificationChatInfo(1842626385L, "skrinka_pandori_lachen", 3));
            storage.chats.add(new VerificationChatInfo(1471803186L, "madyar_lachen_pishe", 3));
            storage.chats.add(new VerificationChatInfo(1976625090L, "nikolaev_vanya", 3));
            storage.chats.add(new VerificationChatInfo(1805544387L, "vanek_nikolaev1", 3));
            storage.chats.add(new VerificationChatInfo(5721687279L, "testDs1_bot", 3));
            storage.chats.add(new VerificationChatInfo(6069541884L, "evorog_gov_bot", 3));
            storage.chats.add(new VerificationChatInfo(5785216795L, "evorog_ua_bot", 3));
            storage.chats.add(new VerificationChatInfo(5956713165L, "evorog_anonim_bot", 3));
            storage.chats.add(new VerificationChatInfo(5440442548L, "evorogina_bot", 3));
            storage.chats.add(new VerificationChatInfo(5324394535L, "evorog_robot", 3));
            storage.chats.add(new VerificationChatInfo(1719705579L, "hochu_zhyut", 3));
            storage.chats.add(new VerificationChatInfo(1844665241L, "hochu_zhyt1", 3));
            storage.chats.add(new VerificationChatInfo(5643974597L, "spasisebya_bot", 3));
            storage.chats.add(new VerificationChatInfo(5698701277L, "spaslsebyabot", 3));
            storage.chats.add(new VerificationChatInfo(5727271350L, "spastisebyabot", 3));
            storage.chats.add(new VerificationChatInfo(5673236403L, "Hochu_Zhyt_Bot", 3));
            storage.chats.add(new VerificationChatInfo(1566061839L, "gdeposjar", 3));
            storage.chats.add(new VerificationChatInfo(1705987207L, "pojar_piter", 3));
            storage.chats.add(new VerificationChatInfo(1829232052L, "firemoscowandRussia", 3));
            storage.chats.add(new VerificationChatInfo(6091365211L, "Kahovskiruhbot", 3));
            storage.chats.add(new VerificationChatInfo(6110012702L, "Kakhovski_ruh_bot", 3));
            storage.chats.add(new VerificationChatInfo(1927064019L, "brdprotiv_me", 3));
            storage.chats.add(new VerificationChatInfo(6221245011L, "brdsprotiv_bot", 3));
            storage.chats.add(new VerificationChatInfo(6267293532L, "brdprotivBot", 3));
            storage.chats.add(new VerificationChatInfo(5734160761L, "brdprotiv_bot", 3));
            storage.chats.add(new VerificationChatInfo(5760847362L, "Suprotyv_brd_bpa_bot", 3));
            storage.chats.add(new VerificationChatInfo(6035643336L, "mrplsprotyv_bot", 3));
            storage.chats.add(new VerificationChatInfo(5399094229L, "peace_da_rusni_bot", 3));
            storage.chats.add(new VerificationChatInfo(6018488147L, "Rusni_peace_da_bot", 3));
            storage.chats.add(new VerificationChatInfo(5887363286L, "Rus_ni_peace_daBot", 3));
            storage.chats.add(new VerificationChatInfo(5857742074L, "Rus_ne_paece_da_bot", 3));
            storage.chats.add(new VerificationChatInfo(6083107910L, "Rusni_peaceda_bot", 3));
            storage.chats.add(new VerificationChatInfo(6499292937L, "osbbelpol_bot", 3));
            storage.chats.add(new VerificationChatInfo(6507022757L, "Black_Map_bot", 3));
            storage.chats.add(new VerificationChatInfo(-1371017531L, "cpartizans_life", 3));
            storage.chats.add(new VerificationChatInfo(1137333814L, "cpartizansbot", 3));
            storage.chats.add(new VerificationChatInfo(5633844875L, "zerkalo_editoranonim_bot", 3));
            storage.chats.add(new VerificationChatInfo(2056958885L, "Supratsiv_support_bot", 3));
            storage.chats.add(new VerificationChatInfo(6174577613L, "charter97_info_bot", 3));
            storage.chats.add(new VerificationChatInfo(5591505925L, "NicolaiKhalezinBot", 3));
            storage.chats.add(new VerificationChatInfo(5208668406L, "MaydayHelpBot", 3));
            storage.chats.add(new VerificationChatInfo(593287940L, "my_new_grodno_bot", 3));
            storage.chats.add(new VerificationChatInfo(865462332L, "nextamail", 3));
            storage.chats.add(new VerificationChatInfo(6171240416L, "ChatHonestP_bot", 3));
            storage.chats.add(new VerificationChatInfo(5431821607L, "Blaganoff2022_bot", 3));
            storage.chats.add(new VerificationChatInfo(5640237945L, "terrorbel_bot", 3));
            storage.chats.add(new VerificationChatInfo(1651774110L, "supratsiv", 3));
            storage.chats.add(new VerificationChatInfo(874340248L, "EURORADIOBOT", 3));
            storage.chats.add(new VerificationChatInfo(5799451422L, "pIan_peramoga_exit_bot", 3));
            storage.chats.add(new VerificationChatInfo(6294664943L, "peramoga_bot", 3));
            storage.chats.add(new VerificationChatInfo(5080158788L, "BYBY_peramogaZOOk_bot", 3));
            storage.chats.add(new VerificationChatInfo(5192128018L, "help_bysol_bot", 3));

            saveRepository();
        } catch (Exception ignore) {
        }
    }

    private void updateCache() {
        cacheTypeByChatId.clear();

        for (VerificationStorage storage : storages) {
            for (VerificationChatInfo chat : storage.chats) {
                cacheTypeByChatId.put(chat.chatId, chat.type);
            }
        }
    }

    private int getChatType(long chatId) {
        ensureRepositoryLoaded();
        Integer type = cacheTypeByChatId.get(chatId);
        return type != null ? type : -1;
    }

    private boolean checkChatType(long chatId, int targetType, boolean targetValue) {
        if (FakePasscodeUtils.isFakePasscodeActivated() || !SharedConfig.additionalVerifiedBadges) {
            return targetValue;
        }
        int type = getChatType(chatId);
        if (type != -1) {
            return type == targetType;
        } else {
            return targetValue;
        }
    }

    public boolean isVerified(long chatId, boolean verified) {
        return checkChatType(chatId, TYPE_VERIFIED, verified);
    }

    public boolean isScam(long chatId, boolean scam) {
        return checkChatType(chatId, TYPE_SCAM, scam);
    }

    public boolean isFake(long chatId, boolean fake) {
        return checkChatType(chatId, TYPE_FAKE, fake);
    }

    public List<VerificationStorage> getStorages() {
        ensureRepositoryLoaded();
        return storages;
    }

    public VerificationStorage getStorage(long chatId) {
        ensureRepositoryLoaded();
        return storages.stream()
                .filter(s -> s.chatId == chatId)
                .findAny()
                .orElse(null);
    }

    public VerificationStorage getStorage(TLRPC.InputPeer peer) {
        ensureRepositoryLoaded();
        return VerificationRepository.getInstance().getStorages().stream()
                .filter(s -> s.chatId == peer.channel_id
                        || s.chatId == -peer.channel_id
                        || s.chatId == peer.chat_id
                        || s.chatId == -peer.chat_id)
                .findAny()
                .orElse(null);
    }

    public void deleteStorage(long chatId) {
        ensureRepositoryLoaded();
        storages.removeIf(s -> s.chatId == chatId);
        saveRepository();
    }

    public void addStorage(String name, String username, long chatId) {
        ensureRepositoryLoaded();
        storages.add(new VerificationStorage(name, username, chatId));
        saveRepository();
    }

    public void saveNextCheckTime(long storageChatId, long lastCheckTime) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> s.updateNextCheckTime(lastCheckTime));
        saveRepository();
    }

    public void saveLastCheckedMessageId(long storageChatId, int lastCheckedMessageId) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> s.lastCheckedMessageId = lastCheckedMessageId);
        saveRepository();
    }

    public void saveRepositoryChatUsername(long storageChatId, String username) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> {
                    if (!s.chatUsername.equals(username)) {
                        s.chatUsername = username;
                        s.chatId = 0;
                        s.lastCheckedMessageId = 0;
                    }
                });
        saveRepository();
    }

    public void saveRepositoryChatId(String storageChatUsername, long chatId) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatUsername.equals(storageChatUsername))
                .forEach(s -> s.chatId = chatId);
        saveRepository();
    }

    public void putChats(long storageChatId, List<VerificationChatInfo> chats) {
        ensureRepositoryLoaded();
        VerificationStorage storage = storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .findAny()
                .orElse(null);
        if (storage == null) {
            return;
        }
        Set<Long> existedIds = storage.chats.stream().map(c -> c.chatId).collect(Collectors.toSet());
        for (VerificationChatInfo chat : chats) {
            if (existedIds.contains(chat.chatId)) {
                storage.chats.removeIf(c -> c.chatId == chat.chatId);
            }
            storage.chats.add(chat);
        }
        saveRepository();
    }

    public void deleteChats(long storageChatId, List<VerificationChatInfo> chats) {
        ensureRepositoryLoaded();
        storages.stream()
                .filter(s -> s.chatId == storageChatId)
                .forEach(s -> s.chats.removeIf(c ->
                        chats.stream().anyMatch(toDelete -> c.type == toDelete.type && c.chatId == toDelete.chatId))
                );
        saveRepository();
    }

    public void ensureRepositoryLoaded() {
        if (!repositoryLoaded) {
            loadRepository();
            repositoryLoaded = true;
        }
    }
}
