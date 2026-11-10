package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.model.PublishJob;
import com.hamzatech.publisher.model.QueueItem;
import com.hamzatech.publisher.model.QueueItemStatus;
import com.hamzatech.publisher.service.PublishQueueService;
import com.hamzatech.publisher.util.AppColors;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;

import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * دفعات النشر التي تُوقّفت قبل اكتمالها — بإغلاق التطبيق أو ضغط زر الإيقاف —
 * ويمكن استئنافها. القرار الفعلي بتشغيل الاستئناف يبقى عند
 * {@code DashboardController}، فهو الذي يملك شريط التقدّم وزر الإيقاف؛ هذه
 * الشاشة تعرض وتختار فقط، ثم تُعيد الوجهة المُختارة له عبر {@link #getJobToResume()}.
 */
@Log4j2
public class PublishQueueController {

    private final PublishQueueService queueService = PublishQueueService.getInstance();
    private final LocaleManager localeManager = LocaleManager.getInstance();

    @FXML
    private ListView<PublishJob> jobsList;
    @FXML
    private TableView<QueueItem> itemsTable;
    @FXML
    private TableColumn<QueueItem, String> colItemStatus;
    @FXML
    private TableColumn<QueueItem, String> colItemTitle;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnResume;
    @FXML
    private Button btnDiscard;

    private ResourceBundle bundle;

    /** الدفعة التي طلب المستخدم استئنافها، أو {@code null} إن أُغلقت الشاشة بلا اختيار. */
    private PublishJob jobToResume;

    @FXML
    public void initialize() {
        bundle = localeManager.getResourceBundle();

        jobsList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(PublishJob job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setText(null);
                } else {
                    long remaining = job.countByStatus(QueueItemStatus.PENDING);
                    setText(MessageFormat.format(bundle.getString("queue.job.summary"),
                            job.getTargetName(), remaining, job.total()));
                }
            }
        });
        jobsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, job) -> displayItems(job));

        colItemTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));

        colItemStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().name()));
        colItemStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String statusName, boolean empty) {
                super.updateItem(statusName, empty);
                if (empty || statusName == null) {
                    setText(null);
                    setTextFill(null);
                } else {
                    QueueItemStatus status = QueueItemStatus.valueOf(statusName);
                    setText(statusLabel(status));
                    setTextFill(Color.web(statusColor(status)));
                }
            }
        });

        loadJobs();
    }

    private void loadJobs() {
        List<PublishJob> jobs = queueService.getResumableJobs();
        jobsList.getItems().setAll(jobs);

        boolean hasJobs = !jobs.isEmpty();
        btnResume.setDisable(!hasJobs);
        btnDiscard.setDisable(!hasJobs);
        lblStatus.setText(hasJobs
                ? MessageFormat.format(bundle.getString("queue.status.count"), jobs.size())
                : bundle.getString("queue.status.empty"));

        if (hasJobs) {
            jobsList.getSelectionModel().selectFirst();
        } else {
            itemsTable.getItems().clear();
        }
    }

    private void displayItems(PublishJob job) {
        itemsTable.getItems().setAll(job == null ? List.of() : job.getItems());
    }

    @FXML
    public void handleResume() {
        PublishJob selected = jobsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        jobToResume = selected;
        closeWindow();
    }

    @FXML
    public void handleDiscard() {
        PublishJob selected = jobsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("queue.confirm.discard.title"));
        alert.setHeaderText(bundle.getString("queue.confirm.discard.message"));
        alert.setContentText(selected.getTargetName());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            queueService.deleteJob(selected.getId());
            log.info("Discarded queued batch for: {}", selected.getTargetName());
            loadJobs();
        }
    }

    @FXML
    public void handleClose() {
        closeWindow();
    }

    private void closeWindow() {
        WorkspaceTabs.close(jobsList);
    }

    /** يُقرأ من {@code DashboardController} بعد إغلاق الشاشة. */
    public PublishJob getJobToResume() {
        return jobToResume;
    }

    private String statusLabel(QueueItemStatus status) {
        return switch (status) {
            case PENDING -> bundle.getString("queue.status.pending");
            case SUCCEEDED -> bundle.getString("queue.status.succeeded");
            case FAILED -> bundle.getString("queue.status.failed");
            case SKIPPED -> bundle.getString("queue.status.skipped");
        };
    }

    private static String statusColor(QueueItemStatus status) {
        return switch (status) {
            case PENDING -> AppColors.GRAY;
            case SUCCEEDED -> AppColors.SUCCESS;
            case FAILED -> AppColors.DANGER;
            case SKIPPED -> AppColors.WARNING;
        };
    }
}
