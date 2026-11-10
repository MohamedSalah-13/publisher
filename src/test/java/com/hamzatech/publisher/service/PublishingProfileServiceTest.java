package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.PublishingProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class PublishingProfileServiceTest {

    private static final PublishTarget TARGET =
            PublishTarget.ofBlogger("blog-profile-test", "Test Blog", "https://example.com");

    private final PublishingProfileService profileService = PublishingProfileService.getInstance();
    private final ConfigService configService = ConfigService.getInstance();

    @AfterEach
    void cleanUp() throws IOException {
        profileService.deleteProfile(TARGET.getId());
        configService.set(ConfigService.AI_PROVIDER, null);
        configService.set(ConfigService.CONTENT_LANGUAGE, null);
        configService.set(ConfigService.PUBLISH_DELAY, null);
        configService.set(ConfigService.POST_STATUS, null);
    }

    @Test
    void resolve_withNoProfile_returnsGlobalDefaultsUnchanged() {
        configService.setContentLanguage("english");
        configService.setPublishDelay(12);

        EffectiveSettings settings = profileService.resolve(TARGET);

        assertEquals("english", settings.contentLanguage());
        assertEquals(12, settings.publishDelaySeconds());
        assertNull(settings.defaultCategory());
    }

    @Test
    void resolve_withNoTarget_stillReturnsGlobalDefaults() {
        // نافذة المعاينة قبل اختيار مدونة: لا وجهة بعد
        configService.setContentLanguage("english");

        assertEquals("english", profileService.resolve(null).contentLanguage());
    }

    @Test
    void aSetFieldOverridesTheGlobalDefault() throws IOException {
        configService.setContentLanguage("arabic");

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setContentLanguage("english");
        profileService.saveProfile(profile);

        assertEquals("english", profileService.resolve(TARGET).contentLanguage());
    }

    @Test
    void anUnsetFieldFallsBackToTheGlobalDefault_evenWithAProfilePresent() throws IOException {
        configService.setSlugType("auto");

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setContentLanguage("english"); // هذا الحقل فقط مُتجاوَز
        profileService.saveProfile(profile);

        // slugType لم يُضبط في الملف، فيجب أن يأتي من الإعداد العام رغم وجود ملف تعريف
        assertEquals("auto", profileService.resolve(TARGET).slugType());
    }

    @Test
    void publishDelayOverride_zeroIsRespected() throws IOException {
        // صفر قيمة صالحة صريحة، ويجب ألا تُلتبَس بـ"غير مضبوط"
        configService.setPublishDelay(30);

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setPublishDelaySeconds(0);
        profileService.saveProfile(profile);

        assertEquals(0, profileService.resolve(TARGET).publishDelaySeconds());
    }

    @Test
    void defaultCategoryHasNoGlobalFallback() throws IOException {
        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setDefaultCategory("سياحة");
        profileService.saveProfile(profile);

        assertEquals("سياحة", profileService.resolve(TARGET).defaultCategory());
    }

    @Test
    void postStatus_defaultsToPublishing() {
        assertEquals(ConfigService.POST_STATUS_PUBLISH, profileService.resolve(TARGET).postStatus());
        assertFalse(profileService.resolve(TARGET).draft());
    }

    @Test
    void postStatus_followsTheGlobalDraftSetting() {
        configService.set(ConfigService.POST_STATUS, ConfigService.POST_STATUS_DRAFT);

        assertTrue(profileService.resolve(TARGET).draft());
    }

    @Test
    void postStatus_canBeOverriddenPerTarget() throws IOException {
        // موقع جديد يُراجَع يدويًا بينما بقية المواقع تنشر مباشرة
        configService.set(ConfigService.POST_STATUS, ConfigService.POST_STATUS_PUBLISH);

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setPostStatus(ConfigService.POST_STATUS_DRAFT);
        profileService.saveProfile(profile);

        assertTrue(profileService.resolve(TARGET).draft());
        assertFalse(profileService.resolve(null).draft());
    }

    @Test
    void aProfileWithNoOverridesIsNotStored() throws IOException {
        // ملف تعريف فارغ لا يستحق ملفًا على القرص
        profileService.saveProfile(new PublishingProfile(TARGET.getId()));

        assertNull(profileService.getProfile(TARGET.getId()));
    }

    @Test
    void savingAgainReplacesTheProfileRatherThanDuplicatingIt() throws IOException {
        PublishingProfile first = new PublishingProfile(TARGET.getId());
        first.setContentLanguage("english");
        profileService.saveProfile(first);

        PublishingProfile second = new PublishingProfile(TARGET.getId());
        second.setContentLanguage("arabic");
        profileService.saveProfile(second);

        assertEquals("arabic", profileService.getProfile(TARGET.getId()).getContentLanguage());
    }

    @Test
    void deletingRemovesTheOverride() throws IOException {
        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setContentLanguage("english");
        profileService.saveProfile(profile);

        profileService.deleteProfile(TARGET.getId());

        assertNull(profileService.getProfile(TARGET.getId()));
    }

    @Test
    void aProfileForOneTargetDoesNotAffectAnother() throws IOException {
        PublishTarget other = PublishTarget.ofBlogger("blog-other", "Other Blog", "https://other.example.com");

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setContentLanguage("english");
        profileService.saveProfile(profile);

        try {
            assertNull(profileService.getProfile(other.getId()));
        } finally {
            profileService.deleteProfile(other.getId());
        }
    }

    @Test
    void blankOverrideStringsFallBackToTheGlobalDefault() throws IOException {
        // الحوار يحفظ null لا نصًا فارغًا، لكن الخدمة يجب ألا تثق بذلك من أي مصدر آخر
        configService.setContentLanguage("arabic");

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setContentLanguage("   ");
        profileService.saveProfile(profile);

        assertEquals("arabic", profileService.resolve(TARGET).contentLanguage());
    }

    @Test
    void sharingDestinations_comeOnlyFromTheProfile_andDefaultToNoSharing() throws IOException {
        assertNull(profileService.resolve(TARGET).telegramChat());
        assertNull(profileService.resolve(TARGET).facebookPageId());

        PublishingProfile profile = new PublishingProfile(TARGET.getId());
        profile.setTelegramChat(" @mychannel ");
        profile.setFacebookPageId("   ");
        assertFalse(profile.isEmpty(), "a profile that only shares is still worth saving");
        profileService.saveProfile(profile);

        EffectiveSettings settings = profileService.resolve(TARGET);
        assertEquals("@mychannel", settings.telegramChat());
        assertNull(settings.facebookPageId(), "a blank page id means no sharing, not an empty destination");
    }
}
