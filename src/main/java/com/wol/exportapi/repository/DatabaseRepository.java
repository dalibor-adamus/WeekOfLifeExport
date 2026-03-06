package com.wol.exportapi.repository;

import com.wol.exportapi.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DatabaseRepository {

    @Autowired
    private ResourceLoader resourceLoader;

    public void initialize(String fileName) throws IOException {
        createNewDatabase(fileName);
        execute(fileName, getResourceAsString("META-INF/SQL/CREATE_TABLE_WEEKS.sql"));
        execute(fileName, getResourceAsString("META-INF/SQL/CREATE_TABLE_DAYS.sql"));
        execute(fileName, getResourceAsString("META-INF/SQL/CREATE_TABLE_DAY_DESCRIPTIONS.sql"));
        execute(fileName, getResourceAsString("META-INF/SQL/CREATE_TABLE_WEEK_COMMENTS.sql"));
    }

    public void initializePhotoOfTheDay(String fileName) throws IOException {
        createNewDatabase(fileName);
        execute(fileName, getResourceAsString("META-INF/SQL/CREATE_TABLE_PHOTO_OF_THE_DAY.sql"));
    }

    public void initializeEditorsChoice(String fileName) throws IOException {
        createNewDatabase(fileName);
        execute(fileName, getResourceAsString("META-INF/SQL/CREATE_TABLE_EDITORS_CHOICE.sql"));
    }

    /**
     * Connect to database. If database doesn't exist, create it.
     *
     * @param fileName the database file name
     */
    private void createNewDatabase(String fileName) {
        try (Connection conn = getConnection(fileName)) {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("The driver name is " + meta.getDriverName());
                System.out.println("A new database has been created.");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void execute(String fileName, String sql) {
        try (Connection conn = getConnection(fileName); Statement stmt = conn.createStatement()) {
            boolean result = stmt.execute(sql);
            if (result) {
                System.out.println("A new database table created");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private Connection getConnection(String fileName) throws SQLException {
        // SQLite connection string
        final String url = "jdbc:sqlite:" + fileName;

        return DriverManager.getConnection(url);
    }

    public long storeWeeks(String fileName, List<Week> weeks) throws SQLException {
        String weekSQL = "INSERT INTO weeks(id, name, url, start_date, end_date, rating, avatar) VALUES(?, ?, ?, ?, ?, ?, ?)";
        String daySQL = "INSERT INTO days(id, week_id, day) VALUES(?, ?, ?)";
        String dayDescriptionSQL = "INSERT INTO day_descriptions(day_id, language, description) VALUES(?, ?, ?)";
        String weekCommentSQL = "INSERT INTO week_comments(week_id, create_date, user, user_name, comment) VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = getConnection(fileName);
             PreparedStatement weekPS = conn.prepareStatement(weekSQL);
             PreparedStatement dayPS = conn.prepareStatement(daySQL);
             PreparedStatement dayDescriptionPS = conn.prepareStatement(dayDescriptionSQL);
             PreparedStatement weekCommentPS = conn.prepareStatement(weekCommentSQL)
        ) {
            for (Week week : weeks) {
                if (exists(fileName, week.getId())) {
                    continue;
                }
                weekPS.setInt(1, week.getId());
                weekPS.setString(2, week.getName());
                weekPS.setString(3, week.getUrl());
                weekPS.setDate(4, Date.valueOf(week.getStartDate()));
                weekPS.setDate(5, Date.valueOf(week.getEndDate()));
                weekPS.setBigDecimal(6, week.getRating());
                weekPS.setBytes(7, week.getAvatar());
                weekPS.executeUpdate();

                for (WeekComment comment : week.getComments()) {
                    weekCommentPS.setInt(1, comment.getWeekId());
                    weekCommentPS.setTimestamp(2, Timestamp.valueOf(comment.getCreateDate()));
                    weekCommentPS.setString(3, comment.getUser());
                    weekCommentPS.setString(4, comment.getUserName());
                    weekCommentPS.setString(5, comment.getComment());
                    weekCommentPS.executeUpdate();
                }

                for (Day day : week.getDays()) {
                    dayPS.setInt(1, day.getId());
                    dayPS.setInt(2, day.getWeekId());
                    dayPS.setDate(3, Date.valueOf(day.getDate()));
                    dayPS.executeUpdate();

                    for (DayDescription dayDescription : day.getDescriptions()) {
                        dayDescriptionPS.setInt(1, day.getId());
                        dayDescriptionPS.setString(2, dayDescription.getLanguage());
                        dayDescriptionPS.setString(3, dayDescription.getDescription());
                        dayDescriptionPS.executeUpdate();
                    }
                }

                week.setStored(true);
            }
        }

        long stored = weeks.stream().filter(Week::isStored).count();
        System.out.println("Stored " + stored + " weeks");
        return stored;
    }

    /**
     * Optimize DB
     */
    public void callVacuum(String fileName) throws SQLException {
        try (Connection conn = getConnection(fileName); PreparedStatement ps = conn.prepareStatement("VACUUM")) {
            ps.executeUpdate();
        }
    }

    public void storeWeekComments(String fileName, Week week) throws SQLException {
        String deleteSQL = "DELETE FROM week_comments WHERE week_id = ?";
        String insertSQL = "INSERT INTO week_comments(week_id, create_date, user, user_name, comment) VALUES(?, ?, ?, ?, ";

        try (Connection conn = getConnection(fileName); PreparedStatement deletePS = conn.prepareStatement(deleteSQL); PreparedStatement insertPS = conn.prepareStatement(insertSQL)) {
            deletePS.setInt(1, week.getId());
            deletePS.executeUpdate();
            for (WeekComment comment : week.getComments()) {
                insertPS.setInt(1, comment.getWeekId());
                insertPS.setTimestamp(2, Timestamp.valueOf(comment.getCreateDate()));
                insertPS.setString(3, comment.getUser());
                insertPS.setString(4, comment.getUserName());
                insertPS.setString(5, comment.getComment());
                insertPS.executeUpdate();
            }
        }
    }

    public void storeDayDescriptions(String fileName, Week week) throws SQLException {
        String deleteSQL = "DELETE FROM day_descriptions WHERE day_id = ?";
        String insertSQL = "INSERT INTO day_descriptions(day_id, language, description) VALUES(?, ?, ?)";

        try (Connection conn = getConnection(fileName); PreparedStatement deletePS = conn.prepareStatement(deleteSQL); PreparedStatement insertPS = conn.prepareStatement(insertSQL)) {
            for (Day day : week.getDays()) {
                deletePS.setInt(1, day.getId());
                deletePS.executeUpdate();
                for (DayDescription dayDescription : day.getDescriptions()) {
                    insertPS.setInt(1, dayDescription.getDayId());
                    insertPS.setString(2, dayDescription.getLanguage());
                    insertPS.setString(3, dayDescription.getDescription());
                    insertPS.executeUpdate();
                }
            }
        }
    }

    public int storePhotoOfTheDay(String fileName, List<PhotoOfTheDay> photos) throws SQLException {
        String sql = "INSERT INTO photo_of_the_day(photo_date, author, author_name, image) VALUES(?, ?, ?, ?)";

        int stored = 0;
        try (Connection conn = getConnection(fileName); PreparedStatement ps = conn.prepareStatement(sql);) {
            for (PhotoOfTheDay photo : photos) {
                if (existsPhotoOfTheDay(fileName, photo.getPhotoDate())) {
                    continue;
                }
                ps.setDate(1, Date.valueOf(photo.getPhotoDate()));
                ps.setString(2, photo.getAuthor());
                ps.setString(3, photo.getAuthorName());
                ps.setBytes(4, photo.getImage());
                ps.executeUpdate();

                stored++;
            }
        }
        return stored;
    }

    public int storeEditorsChoices(String fileName, List<EditorsChoice> editorsChoices) throws SQLException {
        String sql = "INSERT INTO editors_choice(week_id, week_name, week_uri, description, week_date, author, author_name, profession, location, image) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int stored = 0;
        try (Connection conn = getConnection(fileName); PreparedStatement ps = conn.prepareStatement(sql);) {
            for (EditorsChoice editorsChoice : editorsChoices) {
                if (existsEditorsChoice(fileName, editorsChoice.getWeekId())) {
                    continue;
                }
                ps.setInt(1, editorsChoice.getWeekId());
                ps.setString(2, editorsChoice.getWeekName());
                ps.setString(3, editorsChoice.getWeekURI());
                ps.setString(4, editorsChoice.getDescription());
                ps.setDate(5, Date.valueOf(editorsChoice.getWeekDate()));
                ps.setString(6, editorsChoice.getAuthor());
                ps.setString(7, editorsChoice.getAuthorName());
                ps.setString(8, editorsChoice.getProfession());
                ps.setString(9, editorsChoice.getLocation());
                ps.setBytes(10, editorsChoice.getImage());
                ps.executeUpdate();

                stored++;
            }
        }
        return stored;
    }

    private boolean existsEditorsChoice(String fileName, int weekId) throws SQLException {
        String sql = "SELECT week_id FROM editors_choice WHERE week_id=?";

        try (Connection conn = getConnection(fileName); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setInt(1, weekId);
            return ps.executeQuery().next();
        }
    }

    public List<EditorsChoice> getEditorsChoice(String fileName, String userName) throws SQLException {
        String sql = "SELECT week_id, week_name, week_uri, description, week_date, author, author_name, profession, location, image FROM editors_choice";
        if (userName != null && !userName.isBlank()) {
            sql += " WHERE author LIKE '%" + userName + "%'";
        }

        try (Connection conn = getConnection(fileName);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            // loop through the result set
            List<EditorsChoice> editorsChoices = new ArrayList<>();
            while (rs.next()) {
                EditorsChoice editorsChoice = new EditorsChoice();
                editorsChoice.setWeekId(rs.getInt(1));
                editorsChoice.setWeekName(rs.getString(2));
                editorsChoice.setWeekURI(rs.getString(3));
                editorsChoice.setDescription(rs.getString(4));
                editorsChoice.setWeekDate(rs.getDate(5).toLocalDate());
                editorsChoice.setAuthor(rs.getString(6));
                editorsChoice.setAuthorName(rs.getString(7));
                editorsChoice.setProfession(rs.getString(8));
                editorsChoice.setLocation(rs.getString(9));
                editorsChoice.setImage(rs.getBytes(10));

                editorsChoices.add(editorsChoice);
            }
            return editorsChoices;
        }
    }

    public List<Statistics> getEditorsChoicesStatistics(String fileName) throws SQLException {
        String sql = "SELECT author, author_name, count(*) FROM editors_choice GROUP BY author, author_name";
        try (Connection conn = getConnection(fileName);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            // loop through the result set
            List<Statistics> statistics = new ArrayList<>();
            while (rs.next()) {
                Statistics userStatistics = new Statistics();
                userStatistics.setAuthor(rs.getString(1));
                userStatistics.setAuthorName(rs.getString(2));
                userStatistics.setCount(rs.getInt(3));

                statistics.add(userStatistics);
            }
            return statistics;
        }
    }

    public List<PhotoOfTheDay> getPhotoOfTheDay(String fileName, String userName) throws SQLException {
        String sql = "SELECT photo_date, author, author_name, image FROM photo_of_the_day";
        if (userName != null && !userName.isBlank()) {
            sql += " WHERE author LIKE '%" + userName + "%'";
        }

        try (Connection conn = getConnection(fileName);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            // loop through the result set
            List<PhotoOfTheDay> photos = new ArrayList<>();
            while (rs.next()) {
                PhotoOfTheDay photo = new PhotoOfTheDay();
                photo.setPhotoDate(rs.getDate(1).toLocalDate());
                photo.setAuthor(rs.getString(2));
                photo.setAuthorName(rs.getString(3));
                photo.setImage(rs.getBytes(4));

                photos.add(photo);
            }
            return photos;
        }
    }

    public List<Statistics> getPhotoOfTheDayStatistics(String fileName) throws SQLException {
        String sql = "SELECT author, author_name, count(*) FROM photo_of_the_day GROUP BY author, author_name";
        try (Connection conn = getConnection(fileName);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            // loop through the result set
            List<Statistics> photos = new ArrayList<>();
            while (rs.next()) {
                Statistics photo = new Statistics();
                photo.setAuthor(rs.getString(1));
                photo.setAuthorName(rs.getString(2));
                photo.setCount(rs.getInt(3));

                photos.add(photo);
            }
            return photos;
        }
    }

    private boolean existsPhotoOfTheDay(String fileName, LocalDate photoDate) throws SQLException {
        String sql = "SELECT photo_date FROM photo_of_the_day WHERE photo_date=?";

        try (Connection conn = getConnection(fileName); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setDate(1, Date.valueOf(photoDate));
            return ps.executeQuery().next();
        }
    }

    private boolean exists(String fileName, int weekId) throws SQLException {
            String sql = "SELECT id FROM weeks WHERE id=?";

            try (Connection conn = getConnection(fileName); PreparedStatement pstmt  = conn.prepareStatement(sql)){
                pstmt.setInt(1, weekId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next();
            }
    }

    public List<Week> getWeeks(String fileName, int weekId) throws SQLException {
        String sql = "SELECT id, name, url, start_date, end_date, rating, avatar " +
                "FROM weeks " +
                (weekId > 0 ? "WHERE id = " + weekId + " " : "") +
                "ORDER BY id DESC";

        try (Connection conn = getConnection(fileName);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)){

            // loop through the result set
            List<Week> weeks = new ArrayList<>();
            while (rs.next()) {
                Week week = new Week();
                week.setId(rs.getInt(1));
                week.setName(rs.getString(2));
                week.setUrl(rs.getString(3));
                week.setStartDate(rs.getDate(4).toLocalDate());
                week.setEndDate(rs.getDate(5).toLocalDate());
                week.setRating(rs.getBigDecimal(6));
                week.setAvatar(rs.getBytes(7));

                week.setDays(getDays(conn, week.getId()));
                week.setComments(getWeekComments(conn, week.getId()));

                weeks.add(week);
            }

            System.out.println("Loaded " + weeks.size() + " weeks");
            return weeks;
        }
    }

    public List<Day> getDays(Connection conn, int weekId) throws SQLException {
        String sql = "SELECT id, week_id, day FROM days WHERE week_id='" + weekId + "'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            // loop through the result set
            List<Day> days = new ArrayList<>();
            while (rs.next()) {
                Day day = new Day();
                day.setId(rs.getInt(1));
                day.setWeekId(rs.getInt(2));
                day.setDate(rs.getDate(3).toLocalDate());

                day.setDescriptions(getDayDescriptions(conn, day.getId()));

                days.add(day);
            }
            return days;
        }
    }

    public List<WeekComment> getWeekComments(Connection conn, int weekId) throws SQLException {
        String sql = "SELECT week_id, create_date, user, user_name, comment FROM week_comments WHERE week_id='" + weekId + "'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<WeekComment> weekComments = new ArrayList<>();
            while (rs.next()) {
                WeekComment weekComment = new WeekComment();
                weekComment.setWeekId(rs.getInt(1));
                weekComment.setCreateDate(rs.getTimestamp(2).toLocalDateTime());
                weekComment.setUser(rs.getString(3));
                weekComment.setUserName(rs.getString(4));
                weekComment.setComment(rs.getString(5));
                weekComments.add(weekComment);
            }
            return weekComments;
        }
    }

    public List<DayDescription> getDayDescriptions(Connection conn, int dayId) throws SQLException {
        String sql = "SELECT day_id, language, description FROM day_descriptions WHERE day_id='" + dayId + "'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<DayDescription> dayDescriptions = new ArrayList<>();
            while (rs.next()) {
                DayDescription dayDescription = new DayDescription();
                dayDescription.setDayId(rs.getInt(1));
                dayDescription.setLanguage(rs.getString(2));
                dayDescription.setDescription(rs.getString(3));
                dayDescriptions.add(dayDescription);
            }
            return dayDescriptions;
        }
    }

    private String getResourceAsString(String fileName) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + fileName);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Resource " + fileName + " not loaded - " + e.getMessage(), e);
        }
    }

    public static String getResourceAsString(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}