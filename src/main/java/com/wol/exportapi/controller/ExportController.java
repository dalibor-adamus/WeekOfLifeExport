package com.wol.exportapi.controller;

import com.wol.exportapi.model.*;
import com.wol.exportapi.repository.DatabaseRepository;
import com.wol.exportapi.service.WeekOfLifeParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Export Week of Life Data", description = "Export previously stored Week of Life data from SQLite database")
@RestController("/week-of-life/exports")
public class ExportController {

    private DatabaseRepository databaseRepository;

    private WeekOfLifeParser parser;

    @Autowired
    public ExportController(DatabaseRepository databaseRepository, WeekOfLifeParser parser) {
        this.databaseRepository = databaseRepository;
        this.parser = parser;
    }

    @Operation(summary = "Export all stored weeks for a user to an HTML file, including photos, ratings, comments, and day descriptions")
    @GetMapping(value = "/users/{user}/weeks", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportWeeksHTML(@PathVariable("user") @Parameter(example = "dalkos", required = true) String user) throws SQLException {
        List<Week> weeks = databaseRepository.getWeeks(getDatabaseName(user), 0);

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
                .append(".weekBlock {display: inline-block; width: 300px; font-family: Arial, Helvetica Neue, Helvetica, sans-serif;vertical-align: text-top;padding: 10px;}")
                .append(".photo {width: 300px;}")
                .append("</style></head><body>");
        weeks.stream()
                .sorted(Comparator.comparing(Week::getId).reversed())
                .forEach(week -> {
                    html.append("<div class=\"weekBlock\">");
                    html.append("<b>").append("<a href=\"").append(week.getUrl()).append("\">").append(week.getName()).append("</a></b><br />");
                    html.append(week.getStartDate().format(WeekOfLifeParser.DATE_FORMATTER)).append(" - ").append(week.getEndDate().format(WeekOfLifeParser.DATE_FORMATTER)).append(" ").append("<div style=\"float: right;\"><span style=\"color:yellow;\">&#9733;</span> ").append(week.getRating().setScale(2, RoundingMode.HALF_UP)).append("</div><br />");
                    html.append("<img class=\"photo\" src=\"data:image/jpeg;base64,").append(Base64.getEncoder().encodeToString(week.getAvatar())).append("\" />");
                    week.getDays().forEach(day -> {
                        html.append("<a href=\"").append(parser.getDayURL(day)).append("\">").append(day.getDate()).append("</a>").append("<br />");
                        mergeDayDescriptions(week.getId(), day.getDescriptions()).forEach(dayDescription -> html.append("[").append(dayDescription.getLanguage()).append("] ").append(dayDescription.getDescription()).append("<br />"));
                    });
                    if (!week.getComments().isEmpty()) {
                        html.append("<hr />");
                        week.getComments().forEach(weekComment -> html.append("<b><span title=\"").append(weekComment.getUser()).append("\">").append(weekComment.getUserName()).append("</span> [").append(weekComment.getCreateDate().toLocalDate().format(WeekOfLifeParser.DATE_FORMATTER)).append("]</b><br>").append(weekComment.getComment()).append("<br />"));
                    }
                    html.append("</div>");
                });
        html.append("</body></html>");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=%s.html", user))
                .contentLength(html.length())
                .contentType(MediaType.TEXT_HTML)
                .body(html.toString());
    }

    @Operation(summary = "Export all stored 'Photo of the Day' images for a user to an HTML gallery file")
    @GetMapping(value = "/users/{user}/photo-of-the-day", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportPhotoOfTheDayHTML(@PathVariable("user") @Parameter(example = "dalkos", description = "Author name in week of life or its part (if not provided, all will be returned)") String user) throws SQLException {
        List<PhotoOfTheDay> photos = databaseRepository.getPhotoOfTheDay(getDatabaseName("PhotoOfTheDay"), user);

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
                .append(".photoBlock {display: inline-block; width: 400px; height: 320px;font-family: Arial, Helvetica Neue, Helvetica, sans-serif;vertical-align: text-top;}")
                .append(".photo {width: 380px;}")
            .append("</style></head><body>");
        photos.stream()
                .sorted(Comparator.comparing(PhotoOfTheDay::getPhotoDate).reversed())
                .forEach(photo -> {
                    html.append("<div class=\"photoBlock\">");
                    html.append("<b>").append(photo.getPhotoDate().format(WeekOfLifeParser.DATE_FORMATTER)).append("</b> ").append(photo.getAuthorName()).append(" (").append(photo.getAuthor()).append(")").append("<br />");
                    html.append("<img class=\"photo\" src=\"data:image/jpeg;base64,").append(Base64.getEncoder().encodeToString(photo.getImage())).append("\" />");
                    html.append("</div>");
                });
        html.append("</body></html>");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PhotoOfTheDay - " + user + ".html")
                .contentLength(html.length())
                .contentType(MediaType.TEXT_HTML)
                .body(html.toString());
    }

    @Operation(summary = "Retrieve statistics of 'Photo of the Day' contributions by user, sorted by count descending")
    @GetMapping("/statistics/photo-of-the-day")
    public List<Statistics> getPhotoOfTheDayStatistics() throws SQLException {
        return databaseRepository.getPhotoOfTheDayStatistics(getDatabaseName("PhotoOfTheDay")).stream()
                .sorted(Comparator.comparing(Statistics::getCount).reversed())
                .collect(Collectors.toList());
    }

    @Operation(summary = "Export all stored Editor's Choice weeks for a user to an HTML file with descriptions and images")
    @GetMapping(value = "/users/{user}/editors-choices", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportEditorsChoicesHTML(@RequestParam("user") @Parameter(example = "dalkos", description = "Author name in week of life or its part (if not provided, all will be returned)") String user) throws SQLException {
        List<EditorsChoice> photos = databaseRepository.getEditorsChoice(getDatabaseName("EditorsChoice"), user);

        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        photos.stream()
                .sorted(Comparator.comparing(EditorsChoice::getWeekId).reversed())
                .forEach(editorsChoice -> {
                    html.append("<div style=\"display: inline-block; width: 400px; height: 600px; padding: 5px; font-family: Arial, Helvetica Neue, Helvetica, sans-serif;vertical-align: text-top;\">");
                    html.append("<b>").append(editorsChoice.getWeekDate().format(WeekOfLifeParser.DATE_FORMATTER)).append("</b> ").append(editorsChoice.getAuthorName()).append(" (").append(editorsChoice.getAuthor()).append(")").append("<br />");
                    html.append(editorsChoice.getProfession()).append(" / ").append(editorsChoice.getLocation()).append("<br />");
                    html.append("<b><a href=\"").append(WeekOfLifeParser.BASE_URL).append("sk/").append(editorsChoice.getWeekURI()).append("\">").append(editorsChoice.getWeekName()).append("</a></b> [").append(editorsChoice.getWeekId()).append("]").append("<br /><br />");
                    html.append(editorsChoice.getDescription()).append("<br /><br />");
                    html.append("<img style=\"width: 380px;\" src=\"data:image/jpeg;base64,").append(Base64.getEncoder().encodeToString(editorsChoice.getImage())).append("\" />");
                    html.append("</div>");
                });
        html.append("</body></html>");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=EditorsChoice - " + user + ".html")
                .contentLength(html.length())
                .contentType(MediaType.TEXT_HTML)
                .body(html.toString());
    }

    @Operation(summary = "Retrieve statistics of Editor's Choice contributions by user, sorted by count descending")
    @GetMapping("/statistics/editors-choices")
    public List<Statistics> getEditorsChoicesStatistics() throws SQLException {
        return databaseRepository.getEditorsChoicesStatistics(getDatabaseName("EditorsChoice")).stream()
                .sorted(Comparator.comparing(Statistics::getCount).reversed())
                .collect(Collectors.toList());
    }

    private List<DayDescription> mergeDayDescriptions(int weekId, List<DayDescription> dayDescriptions) {
        if (dayDescriptions.isEmpty() || dayDescriptions.size() == 1) {
            return dayDescriptions;
        }

        List<DayDescription> mergedDescriptions = new ArrayList<>();
        dayDescriptions.stream()
                .map(DayDescription::getDescription)
                .distinct()
                .forEach(description -> {
                    String languages = dayDescriptions.stream()
                            .filter(dayDescription -> description.equals(dayDescription.getDescription()))
                            .map(DayDescription::getLanguage)
                            .collect(Collectors.joining("/"));
                    DayDescription dayDescription = new DayDescription();
                    dayDescription.setLanguage(languages);
                    dayDescription.setDescription(description);
                    mergedDescriptions.add(dayDescription);
                });

        if (mergedDescriptions.size() > 1) {
            System.out.println("WEEK " + weekId + " / DAY " + dayDescriptions.get(0).getDayId());
            mergedDescriptions.forEach(dayDescription -> System.out.println(dayDescription.getLanguage() + " " + dayDescription.getDescription()));
        }

        return mergedDescriptions;
    }

    private String getDatabaseName(String databaseName) {
        return databaseName + ".db";
    }
}