package com.wol.exportapi.service;

import com.wol.exportapi.model.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WeekOfLifeParser {

    public static final String BASE_URL = "https://www.weekoflife.com/";
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
    public static final DateTimeFormatter DATE_FORMATTER_US = DateTimeFormatter.ofPattern("M/d/yyyy");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy H:m:s");
    public static final DateTimeFormatter DATE_TIME_FORMATTER_US = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a");

    public List<Week> parseWeeksAndDays(String userName, int page) throws IOException, InterruptedException {
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

            week.setAvatar(getImage(BASE_URL + matcherAvatarURL.group(1)));

            loadDays(week);

            weeks.add(week);
        }
        System.out.println("Parsed " + weeks.size() + " weeks");
        return weeks;
    }

    private void loadDays(Week week) throws IOException, InterruptedException {
        String body = getPageContent(week.getUrl());

        week.setComments(parseWeekComments(week.getId(), body));

        body = removeLineBreaksAndJavaScript(body);

        Matcher matcherRating = PATTERN_WEEK_RATING.matcher(body);
        if (!matcherRating.find()) {
            throw new IllegalStateException("Week " + week.getId() + " has no rating");
        }
        String rating = matcherRating.group(1).replace(',', '.').trim();
        week.setRating(new BigDecimal(rating).setScale(2, RoundingMode.HALF_UP));

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
            String pageContent = getPageContent(getDayURL(day));
            day.setDescriptions(parseDayDescriptions(day.getId(), pageContent));

            week.getDays().add(day);
        }

        if (week.getDays().size() != 7) {
            throw new IllegalStateException("Week " + week.getId() + " detail page contains " + week.getDays().size() + " days");
        }
    }

    public String getDayURL(Day day) {
        return "https://www.weekoflife.com/en/week/" + day.getWeekId() + "/" + day.getId() + "/day.aspx";
    }

    private String removeScripts(String html) {
        final String scriptStart = "<script";
        final String scriptEnd = "</script>";
        while (html.indexOf(scriptStart) > 0) {
            int startIndex = html.indexOf(scriptStart);
            html = html.substring(0, startIndex) + html.substring(html.indexOf(scriptEnd, startIndex) + scriptEnd.length());
        }
        return html;
    }

    public void loadWeekComments(Week week) throws IOException, InterruptedException {
        String pageContent = getPageContent(week.getUrl());
        week.setComments(parseWeekComments(week.getId(), pageContent));
    }

    public List<WeekComment> parseWeekComments(int weekId, String html) {
        html = removeScripts(html);

        int commentsStart = html.indexOf("<div class=\"comments\">");
        if (commentsStart == -1) {
            System.out.println("No comments section");
            return Collections.emptyList();
        }

        List<WeekComment> weekComments = new ArrayList<>();

        int commentStart = html.indexOf("<div class=\"header\">", commentsStart);
        while (commentStart != -1) {
            WeekComment weekComment = new WeekComment();

            weekComment.setWeekId(weekId);

            int userStart = getEndOfText(html, "page/", commentStart);
            int userEnd = getStartOfText(html, "/userweeks.aspx", userStart);
            weekComment.setUser(html.substring(userStart, userEnd).trim());

            int userNameStart = getEndOfText(html, ">", userStart);
            int userNameEnd = getStartOfText(html, "<", userNameStart);
            weekComment.setUserName(html.substring(userNameStart, userNameEnd).trim().replaceAll(" {2}", " "));

            int createDateStart = getEndOfText(html, "<div class=\"dateCreated\">", userNameEnd);
            int createDateEnd = getStartOfText(html, "<", createDateStart);
            weekComment.setCreateDate(LocalDateTime.parse(html.substring(createDateStart, createDateEnd).trim(), DATE_TIME_FORMATTER_US));

            int commentTextStart = getEndOfText(html, "<div class=\"text\">", createDateEnd);
            int commentTextEnd = getStartOfText(html, "</div>", commentTextStart);
            weekComment.setComment(html.substring(commentTextStart, commentTextEnd).trim());

            weekComments.add(weekComment);

            commentStart = html.indexOf("<div class=\"header\">", commentTextEnd);
        }

        return weekComments;
    }

    public void loadDayDescriptions(Week week) throws IOException, InterruptedException {
        for (Day day : week.getDays()) {
            String pageContent = getPageContent(getDayURL(day));
            day.setDescriptions(parseDayDescriptions(day.getId(), pageContent));
        }
    }

    public List<DayDescription> parseDayDescriptions(int dayId, String html) {
        html = removeScripts(html);

        int descriptionsStart = html.indexOf("<div class=\"day_info\">");
        if (descriptionsStart == -1) {
            System.out.println("No descriptions section");
            return Collections.emptyList();
        }

        List<DayDescription> dayDescriptions = new ArrayList<>();

        if (html.indexOf(" id=\"desc_", descriptionsStart) != -1) {
            System.err.println("Comment contains id=\"desc_");
        }

        int descStart = html.indexOf(" id='desc_", descriptionsStart);
        while (descStart != -1) {
            DayDescription dayDescription = new DayDescription();

            dayDescription.setDayId(dayId);

            int langStart = getEndOfText(html, " id='desc_", descStart);
            int langEnd = getStartOfText(html, "'", langStart);
            dayDescription.setLanguage(html.substring(langStart, langEnd).trim());

            int descriptionStart = getEndOfText(html, ">", langEnd);
            int descriptionEnd = getStartOfText(html, "</span>", descriptionStart);
            dayDescription.setDescription(html.substring(descriptionStart, descriptionEnd).trim());

            if (dayDescription.getDescription().contains("<span>")) {
                System.err.println("Comment contains <span>: " + dayDescription.getDescription());
            }
            dayDescriptions.add(dayDescription);

            descStart = html.indexOf(" id='desc_", descriptionEnd);
        }

        return dayDescriptions;
    }

    private String getSubstring(String text, String start, String end) {
        return text.substring(text.indexOf(start) + start.length(), text.indexOf(end)).trim();
    }

    private String getSubstring(String text, String end) {
        return text.substring(0, text.indexOf(end)).trim();
    }

    private byte[] getImage(String path) throws IOException {
        // System.out.println("IMAGE " + path);

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

//    public static void main(String[] args) {
//        String s = "<div class=\"comments\">\n" +
//                "    \n" +
//                "            <div class=\"header\">\n" +
//                "                <div class=\"header_left\">\n" +
//                "                    <a href='https://www.weekoflife.com/en/page/zek/userweeks.aspx'\n" +
//                "                        title='Zdeněk Kamrla'>\n" +
//                "                        Zdeněk Kamrla\n" +
//                "                    </a>\n" +
//                "                    <a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Editor's choice\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/editors_choice_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Redaktor\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/redactor_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Week of Life Master\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/wol_masters_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Member of the Week of Life team\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/wol_team_small.jpg'/></a>\n" +
//                "                </div>\n" +
//                "                <div class=\"dateCreated\">\n" +
//                "                    12. 2. 2021 20:28:46</div>\n" +
//                "                <div class=\"clear_both\">\n" +
//                "                </div>\n" +
//                "            </div>\n" +
//                "            <div class=\"text\">\n" +
//                "                Ladovská zima s dětmi... to je ono... \n" +
//                "            </div>\n" +
//                "            \n" +
//                "        \n" +
//                "            <div class=\"header\">\n" +
//                "                <div class=\"header_left\">\n" +
//                "                    <a href='https://www.weekoflife.com/en/page/JEPE/userweeks.aspx'\n" +
//                "                        title='Josef Písek'>\n" +
//                "                        Josef Písek\n" +
//                "                    </a>\n" +
//                "                    <a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Editor's choice\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/editors_choice_small.jpg'/></a><a class='rolesLink' href='https://www.weekoflife.com/en/roles.aspx' title=\"Week of Life Master\"><img alt='' src='https://www.weekoflife.com//storage/site/roles/wol_masters_small.jpg'/></a>\n" +
//                "                </div>\n" +
//                "                <div class=\"dateCreated\">\n" +
//                "                    12. 2. 2021 20:09:49</div>\n" +
//                "                <div class=\"clear_both\">\n" +
//                "                </div>\n" +
//                "            </div>\n" +
//                "            <div class=\"text\">\n" +
//                "                Pěkné, pěkné...není co dodat...snad jen, že můj vnouček má právě dnes 7 měsíců...:-)\n" +
//                "            </div>";
//        s=s.replaceAll("\n", "");
//        Matcher matcherUser = PATTERN_COMMENT_USER.matcher(s);
//        Matcher matcherCreateDate = PATTERN_COMMENT_DATE.matcher(s);
//        Matcher matcherText = PATTERN_COMMENT_TEXT.matcher(s);
//
//        System.out.println(matcherUser.find() + " "+ matcherCreateDate.find() + " "+ matcherText.find());
//        while (matcherUser.find() && matcherCreateDate.find() && matcherText.find()) {
//            WeekComment comment = new WeekComment();
//            comment.setUser(matcherUser.group(1));
//            comment.setUserName(matcherUser.group(2).substring(matcherUser.group(2).indexOf('>'), matcherUser.group(2).indexOf('<')).trim());
//            String dateCreated = matcherCreateDate.group(1).replaceAll(Pattern.quote(". "), ".").trim();
//            comment.setCreateDate(LocalDateTime.parse(dateCreated, DATE_TIME_FORMATTER));
//            comment.setComment(matcherText.group(1).trim());
//        }
//    }



    public List<PhotoOfTheDay> parsePhotoOfTheDayPage(int page) throws IOException, InterruptedException {
        final String body = getPageContent(BASE_URL + "en/photo_of_the_day.aspx?page=" + page);
        int startIndex = body.indexOf("<div class=\"photo_of_the_day");
        List<PhotoOfTheDay> result = new ArrayList<>();
        while (startIndex != -1) {
            PhotoOfTheDay photoOfTheDay = new PhotoOfTheDay();

            int startIndexAuthorLink = getEndOfText(body, "href=\"", startIndex);
            int endIndexAuthorLink = getStartOfText(body, "\"", startIndexAuthorLink + 1);
            String authorLink = body.substring(startIndexAuthorLink, endIndexAuthorLink);
            if (authorLink.contains("nocni-souteze")) {
                photoOfTheDay.setAuthor("nocni-souteze");
            } else {
                int startIndexAuthor = getEndOfText(body, "page/", startIndex);
                int endIndexAuthor = getStartOfText(body, "/userweeks.aspx", startIndexAuthor);
                photoOfTheDay.setAuthor(body.substring(startIndexAuthor, endIndexAuthor));
            }

            int startIndexAuthorName = getEndOfText(body, ">", endIndexAuthorLink);
            int endIndexAuthorName = getStartOfText(body, "<", startIndexAuthorName);
            photoOfTheDay.setAuthorName(body.substring(startIndexAuthorName, endIndexAuthorName).trim().replaceAll(" {2}", " "));

            int startIndexLink = getEndOfText(body,"href=\"", endIndexAuthorName);
            int endIndexLink = getStartOfText(body, "\"", startIndexLink + 1);
            String url = body.substring(startIndexLink, endIndexLink);

            if (!url.toLowerCase(Locale.ROOT).endsWith(".jpg")) {
                // Error for Zdenek Dvorak photo
                startIndexLink = getEndOfText(body,"href=\"", endIndexLink);
                endIndexLink = getStartOfText(body, "\"", startIndexLink + 1);
                url = body.substring(startIndexLink, endIndexLink);
                System.err.println("Incorrect URL loaded next one: " + url);
            }

            if (url.startsWith("http://")) {
                url = url.replace("http", "https");
            }
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            if (!url.startsWith("http")) {
                url = BASE_URL + url;
            }

            if ("https://www.weekoflife.com/storage/photo/george61/752_752/17171005211206910.jpg".equals(url)) {
                url = "https://www.weekoflife.com/storage/photo/george61/306_230/171005211206910.jpg";
            }

            try {
                photoOfTheDay.setImage(getImage(url));
            } catch (FileNotFoundException e) {
                if (url.contains("JanVrba") || url.contains("jirina.duspivova") || url.contains("Misa5555")
                        || url.contains("denisakufova") || url.contains("MG") || url.contains("K.L")) {
                    photoOfTheDay.setImage(new byte[] {});
                } else {
                    throw e;
                }
            }

            int startIndexDate = getEndOfText(body, "photo_head\">", endIndexLink);
            int endIndexDate = getStartOfText(body, "<", startIndexDate);
            photoOfTheDay.setPhotoDate(LocalDate.parse(body.substring(startIndexDate, endIndexDate).trim(), DATE_FORMATTER));

            startIndex = body.indexOf("<div class=\"photo_of_the_day", endIndexDate);

            result.add(photoOfTheDay);
        }
        return result;
    }

    public List<EditorsChoice> parseEditorsChoicePage(int page) throws IOException, InterruptedException {
        final String body = getPageContent(BASE_URL + "sk/editors_choice.aspx?page=" + page);
        int startIndex = body.indexOf("\"photo_of_the_day\"");
        if (startIndex == -1) {
            return new ArrayList<>();
        }
        List<EditorsChoice> result = new ArrayList<>();
        while (startIndex != -1) {
            EditorsChoice editorsChoice = new EditorsChoice();

            int startIndexLink = getStartOfText(body,"storage/", startIndex);
            int endIndexLink = getEndOfText(body.toLowerCase(), ".jpg", startIndexLink + 1);
            String url = body.substring(startIndexLink, endIndexLink);
            editorsChoice.setImage(getImage(BASE_URL + url));

            int startIndexWeekNameLink = getEndOfText(body, "label\">", endIndexLink);
            int endIndexWeekNameLink = getStartOfText(body, "</div>", startIndexWeekNameLink);
            editorsChoice.setWeekName(body.substring(startIndexWeekNameLink, endIndexWeekNameLink).trim());

            int startIndexAuthor = getEndOfText(body, "page/", startIndex);
            int endIndexAuthor = getStartOfText(body, "/userweeks.aspx", startIndexAuthor);
            editorsChoice.setAuthor(body.substring(startIndexAuthor, endIndexAuthor));

            int startIndexAuthorName = getEndOfText(body, ">", endIndexAuthor);
            int endIndexAuthorName = getStartOfText(body, "<", startIndexAuthorName);
            editorsChoice.setAuthorName(body.substring(startIndexAuthorName, endIndexAuthorName).trim().replaceAll(" {2}", " "));

            int startIndexProfession = getEndOfText(body, ">", getEndOfText(body, "profession.aspx", startIndexAuthorName));
            int endIndexProfession = getStartOfText(body, "<", startIndexProfession);
            editorsChoice.setProfession(body.substring(startIndexProfession, endIndexProfession).trim());

            int startIndexLocation = getEndOfText(body, ">", getEndOfText(body, "location.aspx", endIndexProfession));
            int endIndexLocation = getStartOfText(body, "<", startIndexLocation);
            editorsChoice.setLocation(body.substring(startIndexLocation, endIndexLocation).trim());

            int startIndexDate = getEndOfText(body, "<span>", endIndexLocation);
            int endIndexDate = getStartOfText(body, "</span>", startIndexDate);
            String date = body.substring(startIndexDate, endIndexDate).trim();
            System.out.println(date);
            editorsChoice.setWeekDate(LocalDate.parse(date, DATE_FORMATTER_US));

            int startIndexDescription = getEndOfText(body, "<div class=\"description\">", endIndexDate);
            int endIndexDescription = getStartOfText(body, "</div>", startIndexDescription);
            editorsChoice.setDescription(body.substring(startIndexDescription, endIndexDescription).trim());

            int startIndexWeekUri = getStartOfText(body, "week/", endIndexDescription);
            int endIndexWeekUri = getEndOfText(body, ".aspx", startIndexWeekUri);
            editorsChoice.setWeekURI(body.substring(startIndexWeekUri, endIndexWeekUri).trim());

            int startIndexWeekId = getEndOfText(body, "week/", endIndexDescription);
            int endIndexWeekId = getStartOfText(body, "/", startIndexWeekId + 1);
            editorsChoice.setWeekId(Integer.parseInt(body.substring(startIndexWeekId, endIndexWeekId).trim()));

            startIndex = body.indexOf("photo_of_the_day", endIndexWeekId);

            result.add(editorsChoice);
        }
        return result;
    }

    private int getEndOfText(String html, String text, int offset) {
        return getIndex(html, text, offset) + text.length();
    }

    private int getStartOfText(String html, String text, int offset) {
        return getIndex(html, text, offset);
    }

    private int getIndex(String html, String text, int offset) {
        int index = html.indexOf(text, offset);
        if (index == -1) {
            throw new IllegalStateException("Text '" + text + "' not found from offset " + offset);
        }
        return index;
    }
}