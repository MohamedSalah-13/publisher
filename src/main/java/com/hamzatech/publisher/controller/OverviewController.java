package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.model.DashboardMetrics;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.PublishLogService;
import com.hamzatech.publisher.service.PublishQueueService;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.TilePane;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.NodeOrientation;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class OverviewController {
    @FXML private TilePane metricsTiles;
    @FXML private ComboBox<Integer> periodFilter;
    @FXML private ComboBox<TargetChoice> targetFilter;
    @FXML private Label totalValue, successValue, failureValue, pendingValue, rateValue;
    @FXML private Label activeTargetsValue, providerValue, imageProviderValue, updatedLabel, chartPeriodLabel;
    @FXML private Label insightsLabel;
    @FXML private ProgressBar successProgress;
    @FXML private Button refreshButton;
    @FXML private BarChart<String, Number> activityChart;
    @FXML private TableView<PublishLog> recentTable;
    @FXML private TableColumn<PublishLog, String> titleColumn, targetColumn, statusColumn, dateColumn;
    private ResourceBundle bundle;
    private List<PublishLog> logs = List.of();
    private boolean updatingFilters, loading, refreshAgain;
    private Runnable newBatch = () -> { }, openLogs = () -> { }, openQueue = () -> { };

    public record TargetChoice(String id, String name) {
        @Override public String toString() { return name; }
    }
    private record Snapshot(List<PublishLog> logs, long pending, String provider, String imageProvider) { }

    @FXML public void initialize() {
        bundle = LocaleManager.getInstance().getResourceBundle();
        metricsTiles.prefTileWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            int columns = metricsTiles.getWidth() >= 960 ? 4 : 2;
            return Math.max(220, Math.floor((metricsTiles.getWidth() - 16 * (columns - 1)) / columns));
        }, metricsTiles.widthProperty()));
        periodFilter.setAccessibleText(bundle.getString("overview.period.label"));
        targetFilter.setAccessibleText(bundle.getString("overview.column.target"));
        periodFilter.getItems().setAll(7, 30, 0);
        periodFilter.setConverter(new StringConverter<>() {
            @Override public String toString(Integer days) {
                return bundle.getString(days == 0 ? "overview.period.all" : "overview.period." + days);
            }
            @Override public Integer fromString(String value) { return 7; }
        });
        periodFilter.setValue(7);
        targetFilter.getItems().add(new TargetChoice(null, bundle.getString("overview.targets.all")));
        targetFilter.getSelectionModel().selectFirst();
        periodFilter.setOnAction(e -> renderMetrics());
        targetFilter.setOnAction(e -> { if (!updatingFilters) renderMetrics(); });
        titleColumn.setCellValueFactory(cell -> text(cell.getValue().getTitle()));
        targetColumn.setCellValueFactory(cell -> text(cell.getValue().getBlogName()));
        statusColumn.setCellValueFactory(cell -> text(statusText(cell.getValue())));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("status-success", "status-failure");
                setText(empty ? null : value);
                if (!empty && getTableRow().getItem() != null) {
                    PublishLog log = getTableRow().getItem();
                    if (log.isSuccess()) getStyleClass().add("status-success");
                    else if (log.isFailed()) getStyleClass().add("status-failure");
                }
            }
        });
        dateColumn.setCellValueFactory(cell -> text(cell.getValue().getPublishDate() == null ? "—"
                : cell.getValue().getPublishDate().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))));
        recentTable.setPlaceholder(new Label(bundle.getString("overview.empty")));
        activityChart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        refresh();
    }

    private ReadOnlyStringWrapper text(String value) { return new ReadOnlyStringWrapper(value == null ? "—" : value); }
    private String statusText(PublishLog log) {
        return bundle.getString(log.isSuccess() ? "overview.status.success" : log.isFailed()
                ? "overview.status.failed" : "overview.status.unknown");
    }

    public void setActions(Runnable newBatch, Runnable openLogs, Runnable openQueue) {
        this.newBatch = newBatch;
        this.openLogs = openLogs;
        this.openQueue = openQueue;
    }
    @FXML private void handleNewBatch() { newBatch.run(); }
    @FXML private void handleLogs() { openLogs.run(); }
    @FXML private void handleQueue() { openQueue.run(); }

    /** Called on the FX thread; coalesces requests while the disk snapshot is loading. */
    @FXML public void refresh() {
        if (loading) { refreshAgain = true; return; }
        loading = true;
        refreshButton.setDisable(true);
        Task<Snapshot> task = new Task<>() {
            @Override protected Snapshot call() {
                ConfigService config = ConfigService.getInstance();
                return new Snapshot(PublishLogService.getInstance().getAllLogs(),
                        PublishQueueService.getInstance().getPendingCount(), config.getAIProvider(), config.getImageProvider());
            }
        };
        task.setOnSucceeded(e -> {
            Snapshot snapshot = task.getValue();
            logs = snapshot.logs().stream().filter(Objects::nonNull).toList();
            updateTargets();
            pendingValue.setText(number(snapshot.pending()));
            providerValue.setText(snapshot.provider());
            imageProviderValue.setText(snapshot.imageProvider());
            renderMetrics();
            updatedLabel.setText(java.text.MessageFormat.format(bundle.getString("overview.updated"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))));
            finishRefresh();
        });
        task.setOnFailed(e -> {
            updatedLabel.setText(bundle.getString("overview.refresh.failed"));
            finishRefresh();
        });
        Thread worker = new Thread(task, "dashboard-statistics");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishRefresh() {
        loading = false;
        refreshButton.setDisable(false);
        if (refreshAgain) { refreshAgain = false; refresh(); }
    }

    private void updateTargets() {
        String selected = targetFilter.getValue() == null ? null : targetFilter.getValue().id();
        Map<String, String> targets = new LinkedHashMap<>();
        for (PublishLog log : logs) {
            if (log.getBlogId() != null) targets.putIfAbsent(log.getBlogId(),
                    log.getBlogName() == null ? log.getBlogId() : log.getBlogName());
        }
        updatingFilters = true;
        try {
            targetFilter.getItems().setAll(new TargetChoice(null, bundle.getString("overview.targets.all")));
            targets.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry ->
                    targetFilter.getItems().add(new TargetChoice(entry.getKey(), entry.getValue())));
            targetFilter.setValue(targetFilter.getItems().stream().filter(target -> Objects.equals(target.id(), selected))
                    .findFirst().orElse(targetFilter.getItems().getFirst()));
        } finally { updatingFilters = false; }
    }

    private void renderMetrics() {
        int days = periodFilter.getValue() == null ? 7 : periodFilter.getValue();
        String target = targetFilter.getValue() == null ? null : targetFilter.getValue().id();
        DashboardMetrics metrics = DashboardMetrics.summarize(logs, LocalDate.now(), days, target);
        totalValue.setText(number(metrics.total()));
        successValue.setText(number(metrics.successful()));
        failureValue.setText(number(metrics.failed()));
        activeTargetsValue.setText(number(metrics.activeTargets()));
        NumberFormat percent = NumberFormat.getNumberInstance(LocaleManager.getInstance().getCurrentLocale());
        percent.setMaximumFractionDigits(1);
        rateValue.setText(metrics.successful() + metrics.failed() == 0 ? "—" : percent.format(metrics.successRate()) + "%");
        successProgress.setProgress(metrics.successRate() / 100);
        insightsLabel.setText(bundle.getString(metrics.total() == 0 ? "overview.insight.empty"
                : metrics.successful() + metrics.failed() == 0 ? "overview.insight.unknown"
                : metrics.failed() > 0 ? "overview.insight.failures" : "overview.insight.healthy"));
        chartPeriodLabel.setText(bundle.getString(days == 30 ? "overview.chart.30" : "overview.chart.7"));
        var success = new XYChart.Series<String, Number>();
        var failure = new XYChart.Series<String, Number>();
        success.setName(bundle.getString("overview.status.success"));
        failure.setName(bundle.getString("overview.status.failed"));
        long max = 0;
        for (var day : metrics.activity()) {
            String label = day.date().format(DateTimeFormatter.ofPattern("dd/MM"));
            success.getData().add(new XYChart.Data<>(label, day.successful()));
            failure.getData().add(new XYChart.Data<>(label, day.failed()));
            max = Math.max(max, Math.max(day.successful(), day.failed()));
        }
        var axis = (NumberAxis) activityChart.getYAxis();
        double tick = Math.max(1, Math.ceil(max / 4.0));
        axis.setAutoRanging(false);
        axis.setLowerBound(0);
        axis.setUpperBound(Math.max(4, Math.ceil(max / tick) * tick));
        axis.setTickUnit(tick);
        axis.setMinorTickVisible(false);
        activityChart.getData().setAll(success, failure);
        recentTable.getItems().setAll(metrics.recent());
    }

    private String number(long value) {
        return NumberFormat.getIntegerInstance(LocaleManager.getInstance().getCurrentLocale()).format(value);
    }
}