package com.example.app;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class CrptApi {

    private final HttpClient httpClient;
    private final Semaphore semaphore;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.semaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                0, timeUnit.toSeconds(1), TimeUnit.SECONDS);
    }

    public void createDocument(String url, Document document) {
        try {
            if (!semaphore.tryAcquire()) {
                System.err.println("Превышен лимит запросов");
                return;
            }
            String requestBody = objectMapper.writeValueAsString(document);


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Документ создан");
            } else {
                System.err.println("Ошибка при создании документа. HTTP-статус: " + response.statusCode());
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            System.err.println("Ошибка при отправке запроса: " + e.getMessage());
        } finally {
            semaphore.release();
        }
    }

    public record Document(CrptApi.Document.Description description, String doc_id, String doc_status, String doc_type,
                           boolean importRequest, String owner_inn, String participant_inn, String producer_inn,
                           String production_date, String production_type, List<Product> products, String reg_date,
                           String reg_number) {

        public record Product(String certificate_document, String certificate_document_date,
                              String certificate_document_number, String owner_inn, String producer_inn,
                              String production_date, String tnved_code, String uit_code, String uitu_code) {
        }

        public record Description(String participantInn) {
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 7);
        Document.Product product1 = new Document.Product(
                "document",
                "2024-01-05",
                "6",
                "12345",
                "123049",
                "2024-02-05",
                "1",
                "123123",
                "12300");
        List<Document.Product> products = new ArrayList<>();
        products.add(product1);
        Document document = new Document(new Document.Description("1299945"),
                "1",
                "true",
                "doc_type",
                false,
                "1239945",
                "1992345",
                "1299345",
                "2022-24-05",
                "type1", products,
                "2022-24-05",
                "true");
        crptApi.createDocument("https://ismp.crpt.ru/api/v3/lk/documents/create", document);
    }
}