package com.hamzatech.publisher.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GeminiKeyTester {

    public static void main(String[] args) {
        // الاستخدام:
        // java ... GeminiKeyTester <API_KEY>
        if (args.length == 0 || args[0] == null || args[0].isBlank()) {
            System.out.println("ضع مفتاح API كوسيط عند التشغيل: GeminiKeyTester <API_KEY>");
            return;
        }

        String testApiKey = args[0].trim();

//        testApiKey(testApiKey);

        // 1) اطبع الموديلات المتاحة على v1beta
        listModels(testApiKey, "v1beta");

        // 2) اطبع الموديلات المتاحة على v1
        listModels(testApiKey, "v1");
    }

    private static void testApiKey(String testApiKey) {
        // gemini-1.5-flash يعمل عادة على v1beta
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + testApiKey;

        String jsonPayload = "{\"contents\": [{\"parts\":[{\"text\": \"Hi\"}]}]}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        System.out.println("جاري فحص المفتاح... يرجى الانتظار");

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    System.out.println("--- نتيجة الاختبار ---");
                    System.out.println("Status Code: " + response.statusCode());

                    if (response.statusCode() == 200) {
                        System.out.println("✅ نجاح: المفتاح يعمل والخدمة مفعلة.");
                    } else {
                        System.out.println("❌ فشل: يوجد مشكلة في المفتاح/الإعدادات/الموديل.");
                        System.out.println("تفاصيل الخطأ: " + response.body());
                        analyzeError(response.statusCode());
                    }
                }).join();
    }

    private static void analyzeError(int code) {
        switch (code) {
            case 400 -> System.out.println("نصيحة: الطلب (JSON) غير سليم.");
            case 403 -> System.out.println("نصيحة: المفتاح صحيح لكن API غير مفعلة/صلاحيات غير كافية.");
            case 404 -> System.out.println("نصيحة: الموديل/المسار غير صحيح (جرب v1beta مع 1.5-flash).");
            default -> System.out.println("نصيحة: تحقق من الشبكة أو القيود.");
        }
    }


    private static void listModels(String apiKey, String version) {
        String url = "https://generativelanguage.googleapis.com/" + version + "/models?key=" + apiKey;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        System.out.println("---- ListModels (" + version + ") ----");

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status Code: " + response.statusCode());
            System.out.println(response.body());
        } catch (Exception e) {
            System.out.println("Failed to call ListModels: " + e.getMessage());
        }
    }
}