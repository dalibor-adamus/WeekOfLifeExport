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

@Tag(name = "Week of Life", description = "Parse and persist parsed weeks or day descriptions from Week of Life to SQLite database")
@RestController("/week-of-life")
public class WeekOfLifeResource {

    private DatabaseRepository databaseRepository;

    private WeekOfLifeParser parser;

    @Autowired
    public WeekOfLifeResource(DatabaseRepository databaseRepository, WeekOfLifeParser parser) {
        this.databaseRepository = databaseRepository;
        this.parser = parser;
    }

    @Operation(description = "Parse weeks from Week of Life and store them together with days to local database &lt;user name&gt;.db. It will go from newest weeks until end or until some parsed week is already stored.")
    @PutMapping("/users/{user}/weeks")
    public long storeAllWeeks(
            @PathVariable("user") @Parameter(example = "dalkos", required = true) String user,
            @RequestParam("page") @Parameter(example = "1", description = "Store all not existing editor's choices from given page", required = true) int page,
            @RequestParam("all") @Parameter(example = "true", description = "Continue with next page if some week was stored", required = true) boolean all) throws Exception {
        databaseRepository.initialize(getDatabaseName(user));

        long stored = 0;
        boolean allParsedWeeksStored;
        do {
            List<Week> parsedWeeks = parser.parseWeeksAndDays(user, page);
            long storedCount = databaseRepository.storeWeeks(getDatabaseName(user), parsedWeeks);
            allParsedWeeksStored = storedCount == parsedWeeks.size();
            stored += parsedWeeks.stream().filter(Week::isStored).count();
            page++;
        } while (allParsedWeeksStored && all);

        return stored;
    }

    @Operation(description = "Parse week comments and store them to local database &lt;user name&gt;.db. It will go from newest weeks for selected count of weeks.")
    @PutMapping("/users/{user}/weeks/{week}/comments")
    public void storeWeekComments(
            @PathVariable(value = "user") @Parameter(example = "dalkos") String user,
            @PathVariable(value = "week") @Parameter(example = "26917", description = "Week ID to reload comments for") int weekId) throws Exception {
        List<Week> weeks = databaseRepository.getWeeks(getDatabaseName(user), weekId);
        for (Week week : weeks) {
            parser.loadWeekComments(week);
            databaseRepository.storeWeekComments(getDatabaseName(user), week);
        }
    }

    @PatchMapping("/weeks/vacuum")
    public void storeWeekComments(@RequestParam("userName") @Parameter(example = "dalkos", required = true) String userName) throws Exception {
        databaseRepository.callVacuum(getDatabaseName("dalkos"));
    }

    @Operation(description = "Parse day descriptions for specific week and store them to local database &lt;user name&gt;.db. It will delete existing descriptions")
    @PutMapping("/users/{user}/weeks/{week}/days/descriptions")
    public void storeDayDescriptions(
            @PathVariable(value = "user") @Parameter(example = "dalkos") String user,
            @PathVariable(value = "week") @Parameter(example = "26917", description = "Week ID to reload comments for") int weekId) throws Exception {
        List<Week> weeks = databaseRepository.getWeeks(getDatabaseName(user), weekId);
        for (Week week : weeks) {
            parser.loadDayDescriptions(week);
            databaseRepository.storeDayDescriptions(getDatabaseName(user), week);
        }
    }

    @Operation(description = "Export all stored weeks to HTML")
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

    @Operation(description = "Parse photo of the day Week of Life page and store images together with author and date to local database PhotoOfTheDay.db")
    @PutMapping("/photo-of-the-day")
    public int parsePhotoOfTheDay(
            @RequestParam("page") @Parameter(example = "1", description = "Store all not existing photos from given page", required = true) int page,
            @RequestParam("all") @Parameter(example = "true", description = "Continue with next page if some picture was stored", required = true) boolean all) throws IOException, InterruptedException, SQLException {
        databaseRepository.initializePhotoOfTheDay(getDatabaseName("PhotoOfTheDay"));

        int count = 0;
        List<PhotoOfTheDay> photos = parser.parsePhotoOfTheDayPage(page);
        while (!photos.isEmpty()) {
            int added = databaseRepository.storePhotoOfTheDay(getDatabaseName("PhotoOfTheDay"), photos);
            count += added;
            if (added == 0 || !all) {
                break;
            }
            photos = parser.parsePhotoOfTheDayPage(++page);
        }
        return count;
    }

    @Operation(description = "Export all stored photos of the day to HTML")
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

    @Operation(description = "Get statistics by user name")
    @GetMapping("/statistics/photo-of-the-day")
    public List<Statistics> getPhotoOfTheDayStatistics() throws SQLException {
        return databaseRepository.getPhotoOfTheDayStatistics(getDatabaseName("PhotoOfTheDay")).stream()
                .sorted(Comparator.comparing(Statistics::getCount).reversed())
                .collect(Collectors.toList());
    }

    @Operation(description = "Parse editor's choice page and store week data to EditorsChoice.db")
    @PutMapping("/editors-choices")
    public int parseEditorsChoice(
            @RequestParam("page") @Parameter(example = "1", description = "Store all not existing editor's choices from given page", required = true) int page,
            @RequestParam("all") @Parameter(example = "true", description = "Continue with next page if some editor's choice was stored", required = true) boolean all) throws IOException, InterruptedException, SQLException {
        databaseRepository.initializeEditorsChoice(getDatabaseName("EditorsChoice"));

        int count = 0;
        List<EditorsChoice> editorsChoices = parser.parseEditorsChoicePage(page);
        while (!editorsChoices.isEmpty()) {
            int added = databaseRepository.storeEditorsChoices(getDatabaseName("EditorsChoice"), editorsChoices);
            count += added;
            if (added == 0 || !all) {
                break;
            }
            editorsChoices = parser.parseEditorsChoicePage(++page);
        }
        return count;
    }

    @Operation(description = "Export all stored photos of the day to HTML")
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

    @Operation(description = "Get editor's choices statistics by user name")
    @GetMapping("/statistics/editors-choices")
    public List<Statistics> getEditorsChoicesStatistics() throws SQLException {
        return databaseRepository.getEditorsChoicesStatistics(getDatabaseName("EditorsChoice")).stream()
                .sorted(Comparator.comparing(Statistics::getCount).reversed())
                .collect(Collectors.toList());
    }

    private String getDatabaseName(String databaseName) {
        return databaseName + ".db";
    }
}