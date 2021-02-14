package com.wol.rest.control;

import com.wol.rest.entity.Day;
import com.wol.rest.entity.DayDescription;
import com.wol.rest.entity.Week;
import com.wol.rest.entity.WeekComment;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DatabaseRepository {

    public void initialize(String fileName) throws IOException {
        createNewDatabase(fileName);
        execute(fileName, getResourceAsString("SQL/CREATE_TABLE_WEEKS.sql"));
        execute(fileName, getResourceAsString("SQL/CREATE_TABLE_DAYS.sql"));
        execute(fileName, getResourceAsString("SQL/CREATE_TABLE_DAY_DESCRIPTIONS.sql"));
        execute(fileName, getResourceAsString("SQL/CREATE_TABLE_WEEK_COMMENTS.sql"));
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
                }

                for (Day day : week.getDays()) {
                    dayPS.setInt(1, day.getId());
                    dayPS.setInt(2, day.getWeekId());
                    dayPS.setDate(3, Date.valueOf(day.getDate()));

                    for (DayDescription dayDescription : day.getDescriptions()) {
                        dayDescriptionPS.setInt(1, day.getId());
                        dayDescriptionPS.setString(2, dayDescription.getLanguage());
                        dayDescriptionPS.setString(3, dayDescription.getDescription());
                    }
                }

                week.setStored(true);
            }
        }

        long stored = weeks.stream().filter(Week::isStored).count();
        System.out.println("Stored " + stored + " weeks");
        return stored;
    }

    private boolean exists(String fileName, int weekId) throws SQLException {
            String sql = "SELECT id FROM weeks WHERE id=?";

            try (Connection conn = getConnection(fileName); PreparedStatement pstmt  = conn.prepareStatement(sql)){
                pstmt.setInt(1, weekId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next();
            }
    }

    public List<Week> getWeeks(String fileName) throws SQLException {
        String sql = "SELECT id, name, url, start_date, end_date, avatar FROM weeks";

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

                weeks.add(week);
            }
            System.out.println("Loaded " + weeks.size() + " weeks");
            return weeks;
        }
    }

    private String getResourceAsString(String fileName) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream != null) {
                return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
            throw new IOException("Resource " + fileName + " not found");
        }
    }

}