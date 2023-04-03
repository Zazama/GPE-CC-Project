package de.zazama.cc.workers;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.common.base.Throwables;
import com.google.api.client.json.gson.GsonFactory;

@Configuration
@ExternalTaskSubscription("climate-change-list")
public class ClimateChangeListWorker implements ExternalTaskHandler {

    private final static Logger LOGGER = Logger.getLogger(ClimateChangeListWorker.class.getName());

    @Value("${youtube.api_key}")
    private String youtubeAPIKey;

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            SearchListResponse searchListResponse = this.getVideoListFromYouTube();

            Map<String, String> videosValue = new HashMap<String, String>();
            videosValue.put("html", buildHTMLList(searchListResponse.getItems()));
            
            externalTaskService.complete(externalTask, Collections.singletonMap("videos", videosValue));
            LOGGER.info("Collected " + searchListResponse.getItems().size() + " videos");
        } catch (Exception e) {
            externalTaskService.handleBpmnError(externalTask, "YouTubeAPIError", "Unable to retrieve list of videos right now.");
            LOGGER.warning(Throwables.getStackTraceAsString(e));
        }
    }
    
    private String buildHTMLList(List<SearchResult> searchItems) {
        return
            "<ul>" +
                String.join("", searchItems
                    .stream()
                    .map(this::buildHTMLListPoint)
                    .collect(Collectors.toList())
                ) +
            "</ul>";
    }

    private String buildHTMLListPoint(SearchResult searchItem) {
        return
            "<li>" +
                searchItem.getSnippet().getTitle() + " | " + searchItem.getSnippet().getChannelTitle() +
                "<br />" +
                "<a href=\"" + getURLToVideo(searchItem) + "\" target=\"_blank\">" +
                    getURLToVideo(searchItem) +
                "</a>" +
            "</li>";
    }

    private String getURLToVideo(SearchResult searchItem) {
        return "https://youtu.be/" + searchItem.getId().getVideoId();
    }

    private SearchListResponse getVideoListFromYouTube() throws GeneralSecurityException, IOException {
        return getService()
            .search()
            .list(Arrays.asList("snippet"))
            .setMaxResults(25L)
            .setQ("climate change")
            .setKey(youtubeAPIKey)
            .execute();
    }

    private static YouTube getService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new YouTube.Builder(httpTransport, GsonFactory.getDefaultInstance(), null)
            .setApplicationName("ClimateChangeVideoSearch")
            .build();
    }

}