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

@Tag(name = "Download from Week of Life", description = "Parse and persist parsed weeks or day descriptions from Week of Life to SQLite database")
@RestController("/week-of-life/downloads")
public class DownloadController {

    private DatabaseRepository databaseRepository;

    private WeekOfLifeParser parser;

    @Autowired
    public DownloadController(DatabaseRepository databaseRepository, WeekOfLifeParser parser) {
        this.databaseRepository = databaseRepository;
        this.parser = parser;
    }

    @Operation(description = "Parse weeks from Week of Life and store them together with days to local database &lt;user name&gt;.db. It will go from newest weeks until end or until some parsed week is already stored.")
    @PostMapping("/users/{user}/weeks")
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
    @PostMapping("/users/{user}/weeks/{week}/comments")
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
    @PostMapping("/users/{user}/weeks/{week}/days/descriptions")
    public void storeDayDescriptions(
            @PathVariable(value = "user") @Parameter(example = "dalkos") String user,
            @PathVariable(value = "week") @Parameter(example = "26917", description = "Week ID to reload comments for") int weekId) throws Exception {
        List<Week> weeks = databaseRepository.getWeeks(getDatabaseName(user), weekId);
        for (Week week : weeks) {
            parser.loadDayDescriptions(week);
            databaseRepository.storeDayDescriptions(getDatabaseName(user), week);
        }
    }

    @Operation(description = "Parse photo of the day Week of Life page and store images together with author and date to local database PhotoOfTheDay.db")
    @PostMapping("/photo-of-the-day")
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

    @Operation(description = "Parse editor's choice page and store week data to EditorsChoice.db")
    @PostMapping("/editors-choices")
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

    private String getDatabaseName(String databaseName) {
        return databaseName + ".db";
    }
}