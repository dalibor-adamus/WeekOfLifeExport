package com.wol.rest.boundary;

import com.wol.rest.control.DatabaseRepository;
import com.wol.rest.control.WeekOfLifeParser;
import com.wol.rest.entity.Week;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Tag(name = "Week of Life Persistence", description = "Persist parsed weeks or day descriptions from Week of Life to database")
@Path("/week-of-life")
public class DatabaseResource {

    @Inject
    DatabaseRepository databaseRepository;

    @Inject
    WeekOfLifeParser parser;

    @GET
    @Operation(description = "Parse week descriptions from Week of Life and store them to local database &lt;user name&gt;.db. It will go from newest weeks until end or until some parsed week is already stored.")
    @Path("/weeks")
    public List<Week> storeAllWeeks(@QueryParam("userName") @Parameter(example = "dalkos", required = true) String userName) throws Exception {
        databaseRepository.initialize(getDatabaseName(userName));

        int page = 1;
        List<Week> weeks = new ArrayList<>();
        boolean allParsedWeeksStored;
        do {
            List<Week> parsedWeeks = parser.parseWeeks(userName, page);
            long storedCount = databaseRepository.storeWeeks(getDatabaseName(userName), parsedWeeks);
            allParsedWeeksStored = storedCount == parsedWeeks.size();
            weeks.addAll(parsedWeeks.stream().filter(Week::isStored).collect(Collectors.toList()));
            page++;
        } while (allParsedWeeksStored);

        return weeks;
    }

    @GET
    @Operation(description = "Parse week descriptions from given user's Week of Life page and store them to local database <user name>.db.")
    @Path("/weeks/pages")
    public List<Week> storeWeeks(
            @QueryParam("userName") @Parameter(example = "dalkos", required = true) String userName,
            @QueryParam("page") @Parameter(example = "1", required = true) int page) throws Exception {
        List<Week> weeks = parser.parseWeeks(userName, page);

        databaseRepository.initialize(getDatabaseName(userName));
        databaseRepository.storeWeeks(getDatabaseName(userName), weeks);

        return weeks.stream()
                .filter(Week::isStored)
                .collect(Collectors.toList());
    }

    @GET
    @Operation(description = "Export all stored weeks to HTML")
    @Path("/exports/weeks")
    @Produces(MediaType.TEXT_HTML)
    public String exportWeeksHTML(@QueryParam("userName") @Parameter(example = "dalkos", required = true) String userName) throws SQLException {
        List<Week> weeks = databaseRepository.getWeeks(getDatabaseName(userName));

        StringBuilder html = new StringBuilder();
        html.append("<html><body><table>");
        weeks.stream()
                .sorted(Comparator.comparing(Week::getId).reversed())
                .forEach(week -> {
                    html.append("<tr>");
                    html.append("<td>");
                    html.append("<b>").append(week.getName()).append("</b><br />");
                    html.append(week.getStartDate().format(WeekOfLifeParser.DATE_FORMATTER)).append(" - ").append(week.getEndDate().format(WeekOfLifeParser.DATE_FORMATTER)).append("<br />");
                    html.append("<a href=\"").append(week.getUrl()).append(">");
                    html.append("<img src=\"data:image/jpeg;base64,").append(Base64.getEncoder().encodeToString(week.getAvatar())).append("\" />");
                    html.append("</a>");
                    html.append("</td>");
                    html.append("</tr>");
                });
        html.append("</table></body></html>");

        return html.toString();
    }

    private String getDatabaseName(String userName) {
        return userName + ".db";
    }
}