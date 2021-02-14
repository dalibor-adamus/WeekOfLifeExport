package com.wol.rest.control;

import com.wol.rest.entity.Day;
import com.wol.rest.entity.Week;
import com.wol.rest.entity.WeekComment;

import javax.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class WeekOfLifeParser {

    private static final String BASE_URL = "https://www.weekoflife.com/";

    private static final Pattern PATTERN_NAME = Pattern.compile("<a title='(.+)' onclick=");
    private static final Pattern PATTERN_WEEK_ID = Pattern.compile("https://www.weekoflife.com/en/week/(\\d+)/");
    private static final Pattern PATTERN_WEEK_URL = Pattern.compile("https://www.weekoflife.com/en/week/(.+)aspx");
    private static final Pattern PATTERN_START_DATE = Pattern.compile("<div class=\"cnt2\">(.+)</div>");
    private static final Pattern PATTERN_AVATAR_URL = Pattern.compile("background-image: url\\(../../../(\\S+)\\)\" class=\"pImg\"");

    private static final Pattern PATTERN_DAY_ID = Pattern.compile("<a href=\"https://www.weekoflife.com/en/week/(\\d+)/(\\d+)/day.aspx");
    private static final Pattern PATTERN_WEEK_RATING = Pattern.compile("<span class=\"ratingNum\">(\\d[,]\\d{2})");
    private static final Pattern PATTERN_COMMENT_USER = Pattern.compile("https://www.weekoflife.com/en/page/(\\S+)/userweeks.aspx(.+)</a>");
    private static final Pattern PATTERN_COMMENT_DATE = Pattern.compile("<div class=\"dateCreated\">(.+)</div>");
    private static final Pattern PATTERN_COMMENT_TEXT = Pattern.compile("<div class=\"text\">(.+)</div>");

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy H:m:s");

    public List<Week> parseWeeks(String userName, int page) throws IOException, InterruptedException {
        final String body = getPageContent(BASE_URL + "en/page/" + userName + "/userweeks.aspx?page=" + page);

        Matcher matcherName = PATTERN_NAME.matcher(body);
        Matcher matcherUrl = PATTERN_WEEK_URL.matcher(body);
        Matcher matcherStartDate = PATTERN_START_DATE.matcher(body);
        Matcher matcherWeekId = PATTERN_WEEK_ID.matcher(body);
        Matcher matcherAvatarURL = PATTERN_AVATAR_URL.matcher(body);

        List<Week> weeks = new ArrayList<>();
        while (matcherName.find() && matcherUrl.find() && matcherStartDate.find() && matcherWeekId.find() && matcherAvatarURL.find()) {
            Week week = new Week();
            week.setId(Integer.parseInt(matcherWeekId.group(1)));
            week.setName(matcherName.group(1));
            week.setUrl(matcherUrl.group(/* whole URL */));

            final LocalDate startDate = LocalDate.parse(matcherStartDate.group(1), DATE_FORMATTER);
            week.setStartDate(startDate);
            week.setEndDate(startDate.plusDays(6));

            //TODO uncomment
            //week.setAvatar(getImage(BASE_URL + matcherAvatarURL.group(1)));

            parseWeekDetails(week);

            weeks.add(week);
        }
        System.out.println("Parsed " + weeks.size() + " weeks");
        return weeks;
    }

    private void parseWeekDetails(Week week) throws IOException, InterruptedException {
        String body = getPageContent(week.getUrl());

        try (PrintWriter out = new PrintWriter("c:/Dalo/" + week.getId() + ".html")) {
            out.println(body);
        }

        String text = body;
        final String scriptStart = "<script type=\"text/javascript\">";
        final String scriptEnd = "</script>";
        while (text.indexOf(scriptStart) > 0) {
            int startIndex = text.indexOf(scriptStart);
            text = text.substring(0, startIndex) + text.substring(text.indexOf(scriptEnd, startIndex) + scriptEnd.length());
        }

        try (PrintWriter out = new PrintWriter("c:/Dalo/" + week.getId() + "-2.html")) {
            out.println(text);
        }

        body = removeLineBreaksAndJavaScript(body);



        int dayOffset = 0;
        Matcher matcherDayId = PATTERN_DAY_ID.matcher(body);
        week.setDays(new ArrayList<>());
        while (matcherDayId.find()) {
            Day day = new Day();
            day.setId(Integer.parseInt(matcherDayId.group(2)));
            day.setWeekId(Integer.parseInt(matcherDayId.group(1)));
            if (day.getWeekId() != week.getId()) {
                throw new IllegalStateException("Week " + week.getId() + " detail page contains days from week " + day.getWeekId());
            }
            day.setDate(week.getStartDate().plusDays(dayOffset++));

            //TODO
            day.setDescriptions(new ArrayList<>());

            week.getDays().add(day);
        }

        if (week.getDays().size() != 7) {
            throw new IllegalStateException("Week " + week.getId() + " detail page contains " + week.getDays().size() + " days");
        }

        Matcher matcherRating = PATTERN_WEEK_RATING.matcher(body);
        if (!matcherRating.find()) {
            throw new IllegalStateException("Week " + week.getId() + " has no rating");
        }
        String rating = matcherRating.group(1).replace(',', '.').trim();
        week.setRating(new BigDecimal(rating).setScale(2, RoundingMode.HALF_UP));

//        Matcher matcherUser = PATTERN_COMMENT_USER.matcher(body);
//        Matcher matcherCreateDate = PATTERN_COMMENT_DATE.matcher(body);
//        Matcher matcherText = PATTERN_COMMENT_TEXT.matcher(body);
//        week.setComments(new ArrayList<>());
//        while (matcherUser.find() && matcherCreateDate.find() && matcherText.find()) {
//            System.out.println("matches");
//            WeekComment comment = new WeekComment();
//            comment.setWeekId(week.getId());
//            comment.setUser(matcherUser.group(1));
//            comment.setUserName(getSubstring(matcherUser.group(2), ">", "</a>"));
//            String dateCreated = getSubstring(matcherCreateDate.group(1), "</div>").replaceAll(Pattern.quote(". "), ".");
//            comment.setCreateDate(LocalDateTime.parse(dateCreated, DATE_TIME_FORMATTER));
//            comment.setComment(getSubstring(matcherText.group(1).trim(), "</div>"));
//            week.getComments().add(comment);
//        }

        Matcher matcherUser2 = PATTERN_COMMENT_USER.matcher(body);

        while (matcherUser2.find()) {
            System.out.println("USER " + matcherUser2.group(1) + " " + matcherUser2.group(2));
        }

        Matcher matcherCreateDate2 = PATTERN_COMMENT_DATE.matcher(body);

        while (matcherCreateDate2.find()) {
            System.out.println("DATE " + matcherCreateDate2.group(1).replaceAll(Pattern.quote(". "), "."));
        }

        Matcher matcherText2 = PATTERN_COMMENT_TEXT.matcher(body);

        while (matcherText2.find()) {
            System.out.println("COMENT " + matcherText2.group(1));
        }
    }

    private String getSubstring(String text, String start, String end) {
        return text.substring(text.indexOf(start) + start.length(), text.indexOf(end)).trim();
    }

    private String getSubstring(String text, String end) {
        return text.substring(0, text.indexOf(end)).trim();
    }

    private byte[] getImage(String path) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (InputStream is = new URL(path).openStream()) {
            byte[] byteChunk = new byte[4096]; // Or whatever size you want to read in at a time.
            int n;
            while ((n = is.read(byteChunk)) > 0) {
                os.write(byteChunk, 0, n);
            }
        }
        return os.toByteArray();
    }

    private String removeLineBreaksAndJavaScript(String text) {
        final String scriptStart = "<script type=\"text/javascript\">";
        final String scriptEnd = "</script>";
        while (text.indexOf(scriptStart) > 0) {
            int startIndex = text.indexOf(scriptStart);
            text = text.substring(0, startIndex) + text.substring(text.indexOf(scriptEnd, startIndex) + scriptEnd.length());
        }
        return text.replace("\n", " ").replace("\r", " ");
    }

    private String getPageContent(String uri) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET() // GET is default
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("GET " + uri);

        return response.body();
    }

    public static void main(String[] args) {
        String s = "<div class=\"comments\">\n" +
                "    \n" +
                "            <div class=\"header\">\n" +
                "                <div class=\"header_left\">\n" +
                "                    <a href='https://www.weekoflife.com/en/page/zek/userweeks.aspx'\n" +
                "                        title='Zdeněk Kamrla'>\n" +
                "                        Zdeněk Kamrla\n" +
                "                    </a>\n" +
                "                    <a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Editor's choice\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/editors_choice_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Redaktor\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/redactor_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Week of Life Master\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/wol_masters_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Member of the Week of Life team\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/wol_team_small.jpg'/></a>\n" +
                "                </div>\n" +
                "                <div class=\"dateCreated\">\n" +
                "                    12. 2. 2021 20:28:46</div>\n" +
                "                <div class=\"clear_both\">\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"text\">\n" +
                "                Ladovská zima s dětmi... to je ono... \n" +
                "            </div>\n" +
                "            \n" +
                "        \n" +
                "            <div class=\"header\">\n" +
                "                <div class=\"header_left\">\n" +
                "                    <a href='https://www.weekoflife.com/en/page/JEPE/userweeks.aspx'\n" +
                "                        title='Josef Písek'>\n" +
                "                        Josef Písek\n" +
                "                    </a>\n" +
                "                    <a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Editor's choice\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/editors_choice_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Week of Life Master\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/wol_masters_small.jpg'/></a>\n" +
                "                </div>\n" +
                "                <div class=\"dateCreated\">\n" +
                "                    12. 2. 2021 20:09:49</div>\n" +
                "                <div class=\"clear_both\">\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            <div class=\"text\">\n" +
                "                Pěkné, pěkné...není co dodat...snad jen, že můj vnouček má právě dnes 7 měsíců...:-)\n" +
                "            </div>";
        s=s.replaceAll("\n", "");
        Matcher matcherUser = PATTERN_COMMENT_USER.matcher(s);
        Matcher matcherCreateDate = PATTERN_COMMENT_DATE.matcher(s);
        Matcher matcherText = PATTERN_COMMENT_TEXT.matcher(s);

        System.out.println(matcherUser.find() + " "+ matcherCreateDate.find() + " "+ matcherText.find());
        while (matcherUser.find() && matcherCreateDate.find() && matcherText.find()) {
            WeekComment comment = new WeekComment();
            comment.setUser(matcherUser.group(1));
            comment.setUserName(matcherUser.group(2).substring(matcherUser.group(2).indexOf('>'), matcherUser.group(2).indexOf('<')).trim());
            String dateCreated = matcherCreateDate.group(1).replaceAll(Pattern.quote(". "), ".").trim();
            comment.setCreateDate(LocalDateTime.parse(dateCreated, DATE_TIME_FORMATTER));
            comment.setComment(matcherText.group(1).trim());
        }
    }
}