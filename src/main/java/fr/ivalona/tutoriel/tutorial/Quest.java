package fr.ivalona.tutoriel.tutorial;

import java.util.List;

public record Quest(String id, String title, String description, String bossbar, double reward,
                    List<String> rewardCommands, List<Condition> conditions) {
}
