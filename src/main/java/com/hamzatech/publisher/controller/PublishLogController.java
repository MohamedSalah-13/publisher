package com.hamzatech.publisher.controller;

import com.google.api.services.blogger.model.Blog;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.WordPressSite;
import com.hamzatech.publisher.service.ArticlePublishingService;
import com.hamzatech.publisher.service.BloggerService;
import com.hamzatech.publisher.service.PublishLogService;
import com.hamzatech.publisher.service.WordPressSiteService;
import com.hamzatech.publisher.util.AppColors;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.net.URI;
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class PublishLogController {

    private final PublishLogService logService = PublishLogService.getInstance();
    private final LocaleManager localeManager = LocaleManager.getInstance();
    private final BloggerService bloggerService = BloggerService.getInstance();
    private final Map<String, String> blogUrlCache = new HashMap<>();

    @FXML private TableView<PublishLog> tblLogs;
    @FXML private TableColumn<PublishLog, String> colStatus;
    @FXML private TableColumn<PublishLog, String> colTitle;
    @FXML private TableColumn<PublishLog, String> colBlog;
    @FXML private TableColumn<PublishLog, String> colCategory;
    @FXML private TableColumn<PublishLog, String> colDate;
    @FXML private TableColumn<PublishLog, String> colUrl;
    @FXML private TableColumn<PublishLog, Void> colRetry;

    @FXML private Label lblTotalCount;
    @FXML private Label lblSuccessCount;
    @FXML private Label lblFailedCount;
    @FXML private Label lblSuccessRate;
    @FXML private Label lblStatus;

    @FXML private ComboBox<String> cmbStatusFilter;
    @FXML private ComboBox<String> cmbBlogFilter;
    @FXML private Button btnRefresh;
    @FXML private Button btnClearAll;

    private ResourceBundle bundle;
    private ObservableList<PublishLog> allLogs;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        log.info("Initializing PublishLogController");
        bundle = localeManager.getResourceBundle();

        setupTableColumns();
        setupFilters();
        loadLogs();
    }

    private void setupTableColumns() {
        // Status Column with colored indicator
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                    // الخلايا يُعاد استخدامها، فأزل الصنف وإلا ورثته صفوف فارغة
                    getStyleClass().remove("text-bold");
                } else {
                    PublishLog row = getTableRow() != null ? getTableRow().getItem() : null;
                    if ("SUCCESS".equals(status) && row != null && row.isDraft()) {
                        setText("✎ " + bundle.getString("logs.status.draft"));
                        setTextFill(Color.web(AppColors.WARNING));
                    } else if ("SUCCESS".equals(status)) {
                        setText("✓ " + bundle.getString("logs.status.success"));
                        setTextFill(Color.web(AppColors.SUCCESS));
                    } else {
                        setText("✗ " + bundle.getString("logs.status.failed"));
                        setTextFill(Color.web(AppColors.DANGER));
                    }
                    if (!getStyleClass().contains("text-bold")) {
                        getStyleClass().add("text-bold");
                    }
                }
            }
        });

        // Title Column
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String title, boolean empty) {
                super.updateItem(title, empty);
                if (empty || title == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(title);
                    setTooltip(new Tooltip(title));
                }
            }
        });

        // Blog Column
        colBlog.setCellValueFactory(new PropertyValueFactory<>("blogName"));

        // Category Column
        colCategory.setCellValueFactory(data -> {
            String category = data.getValue().getCategory();
            return new SimpleStringProperty(category != null ? category : "-");
        });

        // Date Column
        colDate.setCellValueFactory(data -> {
            String formatted = data.getValue().getPublishDate().format(dateFormatter);
            return new SimpleStringProperty(formatted);
        });

        // URL Column with clickable link
        colUrl.setCellFactory(col -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink(bundle.getString("logs.view.post"));

            {
                link.setOnAction(e -> {
                    PublishLog log = getTableView().getItems().get(getIndex());
                    openUrl(log.getPostUrl());
                });
            }

            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    PublishLog log = getTableView().getItems().get(getIndex());
                    if (log.isSuccess() && log.getPostUrl() != null) {
                        setGraphic(link);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        // Retry Column (visible only for failed rows)
        colRetry.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button(bundle.getString("logs.retry.button"), new FontIcon("fth-rotate-cw"));

            {
                btn.getStyleClass().add("warning");
                btn.setOnAction(e -> {
                    PublishLog logItem = getTableView().getItems().get(getIndex());
                    handleRetry(logItem, btn);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    PublishLog logItem = getTableView().getItems().get(getIndex());
                    setGraphic(logItem.isFailed() ? btn : null);
                }
            }
        });

        // Enable row selection details on double-click
        tblLogs.setRowFactory(tv -> {
            TableRow<PublishLog> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showLogDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupFilters() {
        // Status Filter
        cmbStatusFilter.getItems().addAll(
                bundle.getString("logs.filter.all"),
                bundle.getString("logs.status.success"),
                bundle.getString("logs.status.failed")
        );
        cmbStatusFilter.getSelectionModel().selectFirst();
        cmbStatusFilter.setOnAction(e -> applyFilters());

        // Blog Filter
        cmbBlogFilter.getItems().add(bundle.getString("logs.filter.all"));
        cmbBlogFilter.getSelectionModel().selectFirst();
        cmbBlogFilter.setOnAction(e -> applyFilters());
    }

    @FXML
    public void handleRefresh() {
        log.info("Refreshing publish logs");
        loadLogs();
    }

    @FXML
    public void handleClearAll() {
        log.info("Clear all logs requested");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("logs.clear.confirm.title"));
        alert.setHeaderText(bundle.getString("logs.clear.confirm.header"));
        alert.setContentText(bundle.getString("logs.clear.confirm.content"));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            logService.clearAllLogs();
            loadLogs();
            lblStatus.setText(bundle.getString("logs.status.cleared"));
            log.info("All logs cleared");
        }
    }

    private void loadLogs() {
        try {
            List<PublishLog> logs = logService.getAllLogs();
            allLogs = FXCollections.observableArrayList(logs);

            // Update blog filter with unique blog names
            Set<String> blogNames = logs.stream()
                    .map(PublishLog::getBlogName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            String currentBlogFilter = cmbBlogFilter.getValue();
            cmbBlogFilter.getItems().clear();
            cmbBlogFilter.getItems().add(bundle.getString("logs.filter.all"));
            cmbBlogFilter.getItems().addAll(blogNames.stream().sorted().toList());
            cmbBlogFilter.setValue(currentBlogFilter != null ? currentBlogFilter : bundle.getString("logs.filter.all"));

            applyFilters();
            updateStatistics();

            lblStatus.setText(bundle.getString("logs.status.loaded"));
            log.info("Loaded {} publish logs", logs.size());
        } catch (Exception e) {
            log.error("Failed to load publish logs", e);
            lblStatus.setText(bundle.getString("logs.status.error.loading"));
        }
    }

    private void applyFilters() {
        if (allLogs == null) return;

        List<PublishLog> filtered = allLogs.stream()
                .filter(this::matchesStatusFilter)
                .filter(this::matchesBlogFilter)
                .toList();

        tblLogs.setItems(FXCollections.observableArrayList(filtered));
    }

    private boolean matchesStatusFilter(PublishLog log) {
        String selected = cmbStatusFilter.getValue();
        if (selected == null || selected.equals(bundle.getString("logs.filter.all"))) {
            return true;
        }
        if (selected.equals(bundle.getString("logs.status.success"))) {
            return log.isSuccess();
        }
        if (selected.equals(bundle.getString("logs.status.failed"))) {
            return log.isFailed();
        }
        return true;
    }

    private boolean matchesBlogFilter(PublishLog log) {
        String selected = cmbBlogFilter.getValue();
        if (selected == null || selected.equals(bundle.getString("logs.filter.all"))) {
            return true;
        }
        return selected.equals(log.getBlogName());
    }

    private void updateStatistics() {
        PublishLogService.PublishStatistics stats = logService.getStatistics();

        lblTotalCount.setText(String.valueOf(stats.total()));
        lblSuccessCount.setText(String.valueOf(stats.successful()));
        lblFailedCount.setText(String.valueOf(stats.failed()));
        lblSuccessRate.setText(String.format("%.1f%%", stats.getSuccessRate()));
    }

    private void showLogDetails(PublishLog log) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(bundle.getString("logs.details.title"));
        alert.setHeaderText(log.getTitle());

        StringBuilder content = new StringBuilder();
        content.append(bundle.getString("logs.table.blog")).append(": ").append(log.getBlogName()).append("\n");
        content.append(bundle.getString("logs.table.status")).append(": ").append(log.getStatus()).append("\n");
        content.append(bundle.getString("logs.table.date")).append(": ").append(log.getPublishDate().format(dateFormatter)).append("\n");

        if (log.getCategory() != null) {
            content.append(bundle.getString("logs.table.category")).append(": ").append(log.getCategory()).append("\n");
        }

        if (log.isSuccess() && log.getPostUrl() != null) {
            content.append(bundle.getString("logs.table.url")).append(": ").append(log.getPostUrl()).append("\n");
        }

        if (log.getSharedTo() != null && !log.getSharedTo().isEmpty()) {
            content.append(bundle.getString("logs.shared.to")).append(": ")
                    .append(String.join(", ", log.getSharedTo())).append("\n");
        }

        if (log.getShareError() != null) {
            content.append(bundle.getString("logs.share.error")).append(": ").append(log.getShareError()).append("\n");
        }

        if (log.isFailed() && log.getErrorMessage() != null) {
            content.append("\n").append(bundle.getString("logs.error.message")).append(":\n").append(log.getErrorMessage());
        }

        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    private void handleRetry(PublishLog logEntry, Button btn) {
        log.info("Retrying failed publish for: {}", logEntry.getTitle());
        btn.setDisable(true);
        lblStatus.setText(MessageFormat.format(bundle.getString("logs.retry.in.progress"), logEntry.getTitle()));

        Thread t = new Thread(() -> {
            PublishTarget target = resolveTarget(logEntry.getBlogId(), logEntry.getBlogName());
            PublishLog result = ArticlePublishingService.publishArticle(
                    logEntry.getTitle(), target, logEntry.getCategory(), null
            );

            Platform.runLater(() -> {
                loadLogs();
                lblStatus.setText(MessageFormat.format(
                        bundle.getString(result.isSuccess() ? "logs.retry.success" : "logs.retry.failed"),
                        logEntry.getTitle()
                ));
            });
        }, "retry-publish");
        t.setDaemon(true);
        t.start();
    }

    /**
     * إعادة بناء وجهة النشر من blogId المخزّن في السجل: موقع WordPress محفوظ إن وُجد، وإلا مدونة Blogger.
     */
    private PublishTarget resolveTarget(String blogId, String blogName) {
        WordPressSite wpSite = WordPressSiteService.getInstance().getSiteById(blogId);
        if (wpSite != null) {
            return PublishTarget.ofWordPress(wpSite);
        }
        return PublishTarget.ofBlogger(blogId, blogName, resolveBlogUrl(blogId));
    }

    private String resolveBlogUrl(String blogId) {
        if (blogUrlCache.containsKey(blogId)) {
            return blogUrlCache.get(blogId);
        }
        try {
            List<Blog> blogs = bloggerService.getMyBlogs();
            for (Blog blog : blogs) {
                blogUrlCache.put(blog.getId(), blog.getUrl());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve blog URL for fallback link building: {}", blogId, e);
        }
        return blogUrlCache.get(blogId);
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) return;

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Opened URL: {}", url);
            }
        } catch (Exception e) {
            log.error("Failed to open URL: {}", url, e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(bundle.getString("logs.error.title"));
            alert.setContentText(bundle.getString("logs.error.open.url") + ": " + url);
            alert.showAndWait();
        }
    }
}
