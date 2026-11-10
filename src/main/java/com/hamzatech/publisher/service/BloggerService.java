package com.hamzatech.publisher.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.blogger.Blogger;
import com.google.api.services.blogger.BloggerScopes;
import com.google.api.services.blogger.model.Blog;
import com.google.api.services.blogger.model.BlogList;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.net.URISyntaxException;
import com.hamzatech.publisher.util.AppPaths;

import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.*;

@Log4j2
public class BloggerService {

    private static final String APPLICATION_NAME = "HamzaTech Blogger Publisher";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Arrays.asList(
            BloggerScopes.BLOGGER,
            "https://www.googleapis.com/auth/userinfo.email"
    );

    // 1) ملف خارجي: في مجلد البيانات، أو بجانب الـ JAR، أو في مجلد التشغيل
    private static final String EXTERNAL_CREDENTIALS_FILE_NAME = GoogleCredentialsFile.FILE_NAME;
    // 2) fallback: الملف داخل resources (للتوافق مع الوضع القديم)
    private static final String RESOURCE_CREDENTIALS_FILE_PATH = "/client_secret.json";

    private static final BloggerService INSTANCE = new BloggerService();

    public static BloggerService getInstance() {
        return INSTANCE;
    }

    private volatile Blogger bloggerClient;

    /** مستقبِل تسجيل الدخول الجاري في المتصفح، إن وُجد؛ إيقافه هو طريقة إلغائه الوحيدة. */
    private final java.util.concurrent.atomic.AtomicReference<LocalServerReceiver> pendingLogin =
            new java.util.concurrent.atomic.AtomicReference<>();

    private BloggerService() {
        log.info("Initializing BloggerService");
        try {
            ensureClientInitialized(false);
            log.debug("BloggerService initialized with existing credentials");
        } catch (Exception e) {
            log.debug("No existing credentials found, user will need to login", e);
        }
    }

    public List<Blog> getMyBlogs() throws IOException, GeneralSecurityException {
        log.debug("Fetching user blogs");
        ensureClientInitialized(false);

        if (bloggerClient == null) {
            log.error("Blogger client is null, user must login first");
            throw new IllegalStateException("يجب تسجيل الدخول أولاً (استدعاء login)");
        }

        BlogList blogList = bloggerClient.blogs().listByUser("self").execute();

        if (blogList.getItems() == null || blogList.getItems().isEmpty()) {
            log.warn("No blogs found for this user");
            return Collections.emptyList();
        }

        log.info("Found {} blogs", blogList.getItems().size());
        return blogList.getItems();
    }

    public String publishPost(String blogId, String title, String content, List<String> labels, boolean isDraft) throws IOException {
        return publishPost(blogId, title, content, labels, isDraft, null);
    }

    /**
     * نشر مقال جديد مع إمكانية تحديد slug مخصص
     */
    public String publishPost(String blogId, String title, String content, List<String> labels, boolean isDraft, String customSlug) throws IOException {
        if (bloggerClient == null) {
            throw new IllegalStateException("يجب تسجيل الدخول أولاً");
        }

        com.google.api.services.blogger.model.Post post = new com.google.api.services.blogger.model.Post();
        post.setTitle(title);
        post.setContent(content);

        if (labels != null && !labels.isEmpty()) {
            post.setLabels(labels);
        }

        // تعيين slug مخصص إذا تم تحديده
        if (customSlug != null && !customSlug.trim().isEmpty()) {
            post.setUrl(customSlug);
            log.debug("Using custom slug: {}", customSlug);
        }

        com.google.api.services.blogger.model.Post insertedPost;
        if (isDraft) {
            insertedPost = bloggerClient.posts().insert(blogId, post).setIsDraft(true).execute();
        } else {
            insertedPost = bloggerClient.posts().insert(blogId, post).execute();
        }

        return insertedPost.getId();
    }

    /**
     * إرجاع الرابط الحقيقي (permalink) للمقال من Blogger API
     */
    public String getPostUrl(String blogId, String postId) throws IOException {
        if (bloggerClient == null) {
            throw new IllegalStateException("يجب تسجيل الدخول أولاً");
        }
        if (blogId == null || blogId.isBlank() || postId == null || postId.isBlank()) {
            return null;
        }

        var post = bloggerClient.posts().get(blogId, postId).execute();
        return post != null ? post.getUrl() : null;
    }

    public java.util.Map<String, Integer> getLabelsWithCount(String blogId) throws IOException {
        if (bloggerClient == null) {
            throw new IllegalStateException("يجب تسجيل الدخول أولاً");
        }

        var postList = bloggerClient.posts().list(blogId).setMaxResults(500L).execute();
        java.util.Map<String, Integer> labelCountMap = new java.util.HashMap<>();

        if (postList.getItems() == null || postList.getItems().isEmpty()) {
            return labelCountMap;
        }

        for (var post : postList.getItems()) {
            if (post.getLabels() != null) {
                for (String label : post.getLabels()) {
                    labelCountMap.put(label, labelCountMap.getOrDefault(label, 0) + 1);
                }
            }
        }

        return labelCountMap;
    }

    public String getUserEmail() throws IOException, GeneralSecurityException {
        ensureClientInitialized(false);

        if (bloggerClient == null) {
            throw new IllegalStateException("يجب تسجيل الدخول أولاً");
        }

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        try (InputStream in = openCredentialsStream()) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory()))
                    .setAccessType("offline")
                    .build();

            Credential credential = flow.loadCredential("user");

            if (credential != null) {
                String url = "https://www.googleapis.com/oauth2/v2/userinfo";
                com.google.api.client.http.GenericUrl userInfoUrl = new com.google.api.client.http.GenericUrl(url);
                com.google.api.client.http.HttpRequest request = httpTransport.createRequestFactory(credential)
                        .buildGetRequest(userInfoUrl);
                com.google.api.client.http.HttpResponse response = request.execute();
                String jsonResponse = response.parseAsString();

                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
                if (json.has("email")) {
                    return json.get("email").getAsString();
                }
            }

            return "غير متصل";
        }
    }

    private void ensureClientInitialized(boolean interactive) throws GeneralSecurityException, IOException {
        if (bloggerClient != null) {
            return;
        }
        if (interactive) {
            authorizeInBrowser();
        } else {
            loadStoredCredential();
        }
    }

    /** جلسة محفوظة من تشغيل سابق، إن وُجدت. سريع ولا ينتظر المستخدم. */
    private synchronized void loadStoredCredential() throws GeneralSecurityException, IOException {
        if (bloggerClient != null) {
            return;
        }
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = newFlow(httpTransport).loadCredential("user");
        if (credential != null) {
            this.bloggerClient = newClient(httpTransport, credential);
        }
    }

    /**
     * تسجيل الدخول عبر المتصفح.
     * <p>
     * غير {@code synchronized} عمدًا: الانتظار هنا بلا مهلة حتى يكمل المستخدم الدخول
     * في المتصفح، وكان يجري وقفل الخدمة ممسوك، فمن ترك المتصفح ودخل اللوحة علّق
     * كل نداء آخر ({@code getMyBlogs}) خلف هذا القفل إلى الأبد.
     */
    private void authorizeInBrowser() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleAuthorizationCodeFlow flow = newFlow(httpTransport);
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        // محاولة سابقة متروكة ما زالت تحجز المنفذ 8888؛ تُلغى لتبدأ هذه
        stopQuietly(pendingLogin.getAndSet(receiver));
        try {
            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
            synchronized (this) {
                this.bloggerClient = newClient(httpTransport, credential);
            }
        } catch (IOException | RuntimeException e) {
            // إيقاف المستقبِل يُرجع الانتظار بلا رمز، فيفشل طلب التوكن بخطأ لا يفهمه
            // المستخدم؛ السبب الحقيقي أن المحاولة أُلغيت
            if (pendingLogin.get() != receiver) {
                throw new IOException("Google sign-in was cancelled.", e);
            }
            throw e;
        } finally {
            pendingLogin.compareAndSet(receiver, null);
        }
    }

    /**
     * يلغي تسجيل دخول ينتظر المتصفح، إن وُجد. يُستدعى عند مغادرة الشاشة التي بدأته.
     */
    public void cancelLogin() {
        LocalServerReceiver receiver = pendingLogin.getAndSet(null);
        if (receiver != null) {
            log.info("Cancelling the pending Google sign-in");
            stopQuietly(receiver);
        }
    }

    private static void stopQuietly(LocalServerReceiver receiver) {
        if (receiver == null) {
            return;
        }
        try {
            receiver.stop();
        } catch (IOException | RuntimeException e) {
            log.debug("Stopping the OAuth receiver failed", e);
        }
    }

    private GoogleAuthorizationCodeFlow newFlow(NetHttpTransport httpTransport) throws IOException {
        try (InputStream in = openCredentialsStream()) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            return new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory()))
                    .setAccessType("offline")
                    .build();
        }
    }

    private static Blogger newClient(NetHttpTransport httpTransport, Credential credential) {
        return new Blogger.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * يفتح ملف OAuth credentials من أول مكان يوجد فيه، بترتيب
     * {@link GoogleCredentialsFile#locate}: مجلد بيانات المستخدم، ثم مجلد الـ JAR،
     * ثم مجلد التشغيل؛ وإن لم يوجد فمن resources ({@code /client_secret.json}).
     */
    private InputStream openCredentialsStream() throws IOException {
        // dataDir() لا dataFile(): الأخير ينقل الملف من مجلد التشغيل، وهو هنا مُدخل لا بيانات
        Optional<Path> external = GoogleCredentialsFile.locate(
                AppPaths.dataDir(), getJarDirectoryOrNull(), Path.of(System.getProperty("user.dir", ".")));
        if (external.isPresent()) {
            log.info("Loading OAuth credentials from: {}", external.get().toAbsolutePath());
            return Files.newInputStream(external.get());
        }

        // fallback: resources
        InputStream in = BloggerService.class.getResourceAsStream(RESOURCE_CREDENTIALS_FILE_PATH);
        if (in != null) {
            log.info("Loading OAuth credentials from resources: {}", RESOURCE_CREDENTIALS_FILE_PATH);
            return in;
        }

        throw new FileNotFoundException(
                "OAuth credentials not found. Place '" + EXTERNAL_CREDENTIALS_FILE_NAME + "' in the app data directory, "
                        + "next to the JAR or in the working directory, or include '" + RESOURCE_CREDENTIALS_FILE_PATH + "' in resources."
        );
    }

    private Path getJarDirectoryOrNull() {
        try {
            var uri = BloggerService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Path.of(uri);

            // إذا كان location ملف jar: ارجع المجلد الأب
            if (Files.isRegularFile(location) && location.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return location.getParent();
            }

            // إذا كان تشغيل من IDE (classes directory): لا نستخدمه كـ jarDir
            return null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /**
     * مجلد رموز OAuth داخل مجلد بيانات المستخدم، مع ترحيل مجلد {@code tokens/}
     * القديم من مجلد العمل مرة واحدة حتى لا يُطالَب المستخدم بتسجيل الدخول من جديد.
     * <p>
     * النقل هنا يتم ملفًا ملفًا وليس بـ {@code Files.move} على المجلد، لأن نقل مجلد
     * بين قرصين مختلفين يفشل في Java — ومجلد المشروع كثيرًا ما يكون على قرص غير
     * قرص {@code %LOCALAPPDATA%}. الاكتفاء بالفشل هنا يعني فقدان الجلسة وبقاء
     * الرمز القديم في مجلد المشروع.
     */
    private static File tokensDirectory() {
        Path target = AppPaths.dataDir().resolve(TOKENS_DIRECTORY_PATH);
        Path legacy = Paths.get(TOKENS_DIRECTORY_PATH).toAbsolutePath();
        if (Files.notExists(target) && Files.isDirectory(legacy) && !legacy.equals(target)) {
            migrateTokens(legacy, target);
        }
        return target.toFile();
    }

    private static void migrateTokens(Path legacy, Path target) {
        try {
            Files.createDirectories(target);
            try (Stream<Path> entries = Files.list(legacy)) {
                for (Path entry : entries.toList()) {
                    if (Files.isRegularFile(entry)) {
                        Files.copy(entry, target.resolve(entry.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            // احذف الأصل فقط بعد نجاح النسخ، حتى لا تُفقد الجلسة إذا تعثّر شيء
            try (Stream<Path> entries = Files.list(legacy)) {
                for (Path entry : entries.toList()) {
                    Files.deleteIfExists(entry);
                }
            }
            Files.deleteIfExists(legacy);
            log.info("Moved OAuth tokens into the app data directory: {}", target);
        } catch (IOException e) {
            log.warn("Could not migrate the tokens directory into {}; you may need to log in again: {}",
                    target, e.getMessage());
        }
    }

    public void clearCredentials() {
        bloggerClient = null;
        File file = tokensDirectory();
        if (file.exists()) {
            for (File f : Objects.requireNonNull(file.listFiles())) {
                f.delete();
            }
        }
    }

    /**
     * هل يوجد ملف OAuth في أيٍّ من أماكن البحث؟ بدونه لا يبدأ تسجيل الدخول أصلًا،
     * فمعالج الإعداد يسأل قبل أن يعرض الزر.
     */
    public boolean hasCredentialsFile() {
        try (InputStream in = openCredentialsStream()) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    public void login() throws GeneralSecurityException, IOException {
        log.info("Starting login process");
        ensureClientInitialized(true);
        log.info("Login successful! Token saved in: {}", tokensDirectory().getAbsolutePath());
    }

    /**
     * توليد slug من العنوان
     * يحول العنوان إلى slug مناسب لـ URL
     */
    public static String generateSlugFromTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }

        // تحويل إلى أحرف صغيرة
        String slug = title.toLowerCase();

        // إزالة الحركات العربية والرموز الخاصة
        slug = slug.replaceAll("[ًٌٍَُِّْ]", "");

        // استبدال المسافات والرموز بـ "-"
        slug = slug.replaceAll("[\\s\\-\\+\\_\\.\\,\\;\\:\\!\\/\\\\\\(\\)\\[\\]\\{\\}\\<\\>\\?\\@\\#\\$\\%\\^\\&\\*\\=\\|\\~\\`\\'\\\"]+", "-");

        // إزالة أي رموز أخرى غير مسموح بها
        slug = slug.replaceAll("[^a-z0-9\\-\u0600-\u06FF]", "");

        // إزالة الشرطات المتعددة
        slug = slug.replaceAll("-+", "-");

        // إزالة الشرطات من البداية والنهاية
        slug = slug.replaceAll("^-+|-+$", "");

        // الحد الأقصى للطول
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
            // التأكد من عدم القطع في منتصف الكلمة
            int lastDash = slug.lastIndexOf('-');
            if (lastDash > 50) {
                slug = slug.substring(0, lastDash);
            }
        }

        return slug;
    }

    /**
     * توليد slug إنجليزي من النص المترجم
     * مخصص للروابط الإنجليزية (SEO-friendly)
     */
    public static String generateEnglishSlug(String englishText) {
        if (englishText == null || englishText.isEmpty()) {
            return "";
        }

        // تحويل إلى أحرف صغيرة
        String slug = englishText.toLowerCase().trim();

        // استبدال المسافات والرموز بـ "-"
        slug = slug.replaceAll("[\\s\\-\\+\\_\\.\\,\\;\\:\\!\\/\\\\\\(\\)\\[\\]\\{\\}\\<\\>\\?\\@\\#\\$\\%\\^\\&\\*\\=\\|\\~\\`\\'\\\"]+", "-");

        // إزالة أي رموز غير إنجليزية وأرقام
        slug = slug.replaceAll("[^a-z0-9\\-]", "");

        // إزالة الشرطات المتعددة
        slug = slug.replaceAll("-+", "-");

        // إزالة الشرطات من البداية والنهاية
        slug = slug.replaceAll("^-+|-+$", "");

        // الحد الأقصى للطول
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
            // التأكد من عدم القطع في منتصف الكلمة
            int lastDash = slug.lastIndexOf('-');
            if (lastDash > 50) {
                slug = slug.substring(0, lastDash);
            }
        }

        return slug;
    }
}