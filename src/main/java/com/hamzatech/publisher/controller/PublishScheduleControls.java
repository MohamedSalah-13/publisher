package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.util.LocaleManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import lombok.extern.log4j.Log4j2;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * أدوات جدولة النشر في اللوحة: خانة التفعيل، والتاريخ والوقت، والعدّ التنازلي، وزر الإلغاء.
 * <p>
 * نُقلت من {@link DashboardController} مع حالتها (المنفّذ المؤقت والعدّاد)، فاللوحة
 * لا تعرف إلا "جدوِل هذا الوقت" و"ألغِ". ما يحدث عند حلول الوقت يقرّره من أنشأها
 * ({@code onFire})، وهو بدء النشر من الملف المختار.
 * <p>
 * الجدولة تعيش ما دام التطبيق مفتوحًا فقط؛ إغلاقه يلغيها.
 */
@Log4j2
final class PublishScheduleControls {

    private final ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
    private final CheckBox chkSchedule;
    private final DatePicker dpScheduleDate;
    private final Spinner<Integer> spnScheduleHour;
    private final Spinner<Integer> spnScheduleMinute;
    private final Label lblScheduleStatus;
    private final Button btnCancelSchedule;
    private final Runnable onFire;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;
    private Timeline countdownTimeline;

    /**
     * @param onFire يُستدعى على خيط الواجهة عند حلول الوقت المجدول
     */
    PublishScheduleControls(CheckBox chkSchedule, DatePicker dpScheduleDate,
                            Spinner<Integer> spnScheduleHour, Spinner<Integer> spnScheduleMinute,
                            Label lblScheduleStatus, Button btnCancelSchedule, Runnable onFire) {
        this.chkSchedule = chkSchedule;
        this.dpScheduleDate = dpScheduleDate;
        this.spnScheduleHour = spnScheduleHour;
        this.spnScheduleMinute = spnScheduleMinute;
        this.lblScheduleStatus = lblScheduleStatus;
        this.btnCancelSchedule = btnCancelSchedule;
        this.onFire = onFire;

        // قيم افتراضية للتاريخ/الوقت، والأدوات مفعّلة فقط عند تحديد خانة الجدولة
        spnScheduleHour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, LocalTime.now().getHour()));
        spnScheduleMinute.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        dpScheduleDate.setValue(LocalDate.now());

        dpScheduleDate.setDisable(true);
        spnScheduleHour.setDisable(true);
        spnScheduleMinute.setDisable(true);

        chkSchedule.selectedProperty().addListener((obs, oldVal, enabled) -> {
            dpScheduleDate.setDisable(!enabled);
            spnScheduleHour.setDisable(!enabled);
            spnScheduleMinute.setDisable(!enabled);
        });
    }

    /** هل اختار المستخدم النشر لاحقًا بدل الآن؟ */
    boolean isRequested() {
        return chkSchedule.isSelected();
    }

    /** الوقت المختار، أو {@code null} إن لم يُختَر تاريخ. */
    LocalDateTime scheduledTime() {
        LocalDate date = dpScheduleDate.getValue();
        if (date == null) return null;
        return LocalDateTime.of(date, LocalTime.of(spnScheduleHour.getValue(), spnScheduleMinute.getValue()));
    }

    void schedule(LocalDateTime when) {
        log.info("Scheduling auto-publishing for: {}", when);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "scheduled-publish");
            th.setDaemon(true);
            return th;
        });

        long delayMillis = Duration.between(LocalDateTime.now(), when).toMillis();
        scheduledFuture = scheduler.schedule(() -> Platform.runLater(onFire), delayMillis, TimeUnit.MILLISECONDS);

        setDisabled(true);
        btnCancelSchedule.setVisible(true);
        btnCancelSchedule.setManaged(true);

        startCountdown(when);
    }

    /** يلغي الجدولة القائمة ويعيد الأدوات قابلة للاستخدام. */
    void cancel() {
        log.info("Publishing schedule cancelled by user");
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        stopCountdown();
        setDisabled(false);
    }

    /** يوقف العدّ التنازلي ويخفي زر الإلغاء — عند بدء النشر فعلًا. */
    void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
        btnCancelSchedule.setVisible(false);
        btnCancelSchedule.setManaged(false);
        lblScheduleStatus.setText("");
    }

    void setDisabled(boolean disabled) {
        chkSchedule.setDisable(disabled);
        boolean scheduleActive = chkSchedule.isSelected() && !disabled;
        dpScheduleDate.setDisable(!scheduleActive);
        spnScheduleHour.setDisable(!scheduleActive);
        spnScheduleMinute.setDisable(!scheduleActive);
    }

    private void startCountdown(LocalDateTime target) {
        countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> updateCountdownLabel(target)));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
        updateCountdownLabel(target);
    }

    private void updateCountdownLabel(LocalDateTime target) {
        Duration remaining = Duration.between(LocalDateTime.now(), target);
        if (remaining.isNegative()) {
            lblScheduleStatus.setText(bundle.getString("dashboard.schedule.starting"));
            return;
        }
        lblScheduleStatus.setText(MessageFormat.format(
                bundle.getString("dashboard.schedule.countdown"),
                remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart()));
    }
}
