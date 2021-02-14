package com.wol.rest.entity;

import java.time.LocalDate;
import java.util.List;

public class Day {

    private int id;
    private int weekId;
    private LocalDate date;
    private List<DayDescription> descriptions;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWeekId() {
        return weekId;
    }

    public void setWeekId(int weekId) {
        this.weekId = weekId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<DayDescription> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(List<DayDescription> descriptions) {
        this.descriptions = descriptions;
    }
}
