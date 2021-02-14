package com.wol.rest.entity;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTransient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonbPropertyOrder({"id", "name", "url", "startDate", "endDate", "rating"})
public class Week {

    private int id;
    private String name;
    private String url;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rating;

    private List<Day> days;
    private List<WeekComment> comments;

    @JsonbTransient
    @Schema(hidden = true)
    private byte[] avatar;

    @JsonbTransient
    @Schema(hidden = true)
    private boolean stored;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    public boolean isStored() {
        return stored;
    }

    public void setStored(boolean stored) {
        this.stored = stored;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public List<Day> getDays() {
        return days;
    }

    public void setDays(List<Day> days) {
        this.days = days;
    }

    public List<WeekComment> getComments() {
        return comments;
    }

    public void setComments(List<WeekComment> comments) {
        this.comments = comments;
    }
}
