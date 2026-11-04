package com.simpleblood.compat;

import com.simpleblood.Chat;
import com.simpleblood.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class KnownIssues {
    private KnownIssues() {}

    private record Issue(String modId, String modName, String key) {}

    private static final List<Issue> ISSUES = List.of(
            new Issue("epicfight", "Epic Fight", "simpleblood.known_issue.epicfight"),
            new Issue("mc2_interactivefoliage", "Interactive Foliage", "simpleblood.known_issue.interactive_foliage"),
            new Issue("sway", "Sway", "simpleblood.known_issue.sway"));

    private static boolean told;

    public static void tick(Minecraft mc) {
        if (told || mc.player == null) return;
        told = true;
        List<String> installed = Platform.modIds();
        for (Issue issue : ISSUES) {
            if (!installed.contains(issue.modId())) continue;
            Chat.say(Component.translatable("simpleblood.known_issue.prefix").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.translatable("simpleblood.known_issue.mod", issue.modName()).withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable(issue.key()).withStyle(ChatFormatting.GRAY)));
        }
    }
}
