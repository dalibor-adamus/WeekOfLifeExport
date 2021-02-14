package com.wol.rest.boundary;

import com.wol.rest.control.WeekOfLifeParser;
import com.wol.rest.entity.Week;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Week of Life Parsers", description = "Parse weeks or day descriptions from Week of Life")
@Path("/parsers")
public class WeekOfLifeResource {

    @Inject
    WeekOfLifeParser parser;

    @GET
    @Path("/weeks")
    public List<Week> parseWeeks(
            @QueryParam("userName") @Parameter(example = "dalkos", required = true) String userName,
            @QueryParam("page") @Parameter(example = "1", required = true) int page) throws IOException, InterruptedException {
        List<Week> weeks = parser.parseWeeks(userName, page);

        return weeks.stream().sorted(Comparator.comparing(Week::getId)).collect(Collectors.toList());
    }

}