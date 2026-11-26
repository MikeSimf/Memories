package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Хроника кампании: сессия, её истории и точки на картах. */
public class ChronicleDto {
    public String id;
    public String name;
    public String date;
    public List<RetellingRef> retellings = new ArrayList<>();
    public List<Point> points = new ArrayList<>();

    public static class RetellingRef {
        public String id;
        public String title;
    }

    public static class Point {
        public String id;
        public String chronicleId;
        public String mapId;
        public String mapName;
        public String heroId;
        public String heroName;
        public String heroColor;
        public boolean wholeTeam;
        public double x;
        public double y;
        public int order;
        public boolean teleport;
    }
}
