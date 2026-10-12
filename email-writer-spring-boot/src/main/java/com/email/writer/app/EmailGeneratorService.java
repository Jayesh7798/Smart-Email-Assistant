package com.email.writer.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generate(EmailRequest emailRequest) {
        // build prompt
        String prompt = buildPrompt(emailRequest);

        // create request: {"contents":[{"parts":[{"text":"..."}]}]}
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        // call Gemini
        String response;
        try {
            response = webClient.post()
                    .uri(geminiApiUrl)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException.TooManyRequests e) {
            System.out.println("Gemini 429: " + e.getResponseBodyAsString());
            return "Rate limit reached. Please wait a minute and try again.";
        } catch (WebClientResponseException e) {
            System.out.println("Gemini error " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            return "Gemini API error (" + e.getStatusCode().value() + "). Please try again.";
        }

        // extract and return the reply text
        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            return rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            return "Error processing request: " + e.getMessage();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Write a reply to the email below. ")
                .append("Do not include a subject line. ")
                .append("Do not use placeholders like [Name] or [Day, Date]; ")
                .append("write a complete reply that can be sent as is, ")
                .append("and end with a short sign-off without a name. ");
        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
        }
        prompt.append("\n\nOriginal email:\n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }
}