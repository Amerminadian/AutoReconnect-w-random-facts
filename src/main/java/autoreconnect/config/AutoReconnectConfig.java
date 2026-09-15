package autoreconnect.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoReconnectConfig {
    private static final String FILE_NAME = "autoreconnect.json";
    private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();
    private static AutoReconnectConfig instance;

    static final List<Integer> defaultDelays = List.of(3, 10, 30, 60);
    static final int defaultDelay = 10;
    static final boolean defaultInfinite = false;
    static final List<AutoMessages> defaultAutoMessages = List.of();

    List<Integer> delays = defaultDelays;
    boolean infinite = defaultInfinite;
    List<AutoMessages> autoMessages = defaultAutoMessages;

    private AutoReconnectConfig() { }

    public static AutoReconnectConfig getInstance() {
        return instance;
    }

    public static void load() {
        try {
            instance = GSON.fromJson(
                    Files.readString(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME)),
                    AutoReconnectConfig.class);
            instance.validate();
        } catch (IOException | JsonSyntaxException ex) {
            LogUtils.getLogger().warn("AutoReconnect could not load the config", ex);
            instance = new AutoReconnectConfig();
            instance.save();
        }
    }

    private void validate() {
        if (delays == null) delays = defaultDelays;
        else if (!delays.isEmpty()) delays = delays.stream().filter(i -> i > 0).toList();
        if (autoMessages == null) autoMessages = defaultAutoMessages;
        else if (!autoMessages.isEmpty()) {
            for (AutoMessages autoMessage : autoMessages) {
                if (autoMessage.name == null) autoMessage.name = AutoMessages.defaultName;
                if (autoMessage.messages == null) autoMessage.messages = AutoMessages.defaultMessages;
                else if (!autoMessage.messages.isEmpty())
                    autoMessage.messages = autoMessage.messages.stream().filter(Objects::nonNull).toList();
                if (autoMessage.delay <= 0) autoMessage.delay = AutoMessages.defaultDelay;

                // Safety validation for random facts
                if (autoMessage.factPrefix == null) autoMessage.factPrefix = AutoMessages.defaultFactPrefix;
                if (autoMessage.facts == null) {
                    autoMessage.facts = new ArrayList<>(AutoMessages.defaultFacts);
                } else {
                    autoMessage.facts = autoMessage.facts.stream()
                            .filter(f -> f != null && !f.trim().isEmpty())
                            .toList();
                }
            }
        }
    }

    public void save() {
        try {
            Files.writeString(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME), GSON.toJson(this));
        } catch (IOException ex) {
            LogUtils.getLogger().error("AutoReconnect could not save the config", ex);
        }
    }

    public int getDelayForAttempt(int attempt) {
        if (attempt < delays.size()) return delays.get(attempt);
        if (infinite) return delays.get(delays.size() - 1);
        return -1;
    }

    public boolean hasAttempts() {
        return !delays.isEmpty();
    }

    public Optional<AutoMessages> getAutoMessagesForName(String name) {
        return autoMessages.stream().filter(autoMessage -> name.equals(autoMessage.name)).findFirst();
    }

    public static final class AutoMessages {
        static final String defaultName = "";
        static final List<String> defaultMessages = List.of();
        static final int defaultDelay = 1000;
        static final boolean defaultEnableRandomFacts = false;
        static final String defaultFactPrefix = "Reconnected! Fun Fact: ";
        static final List<String> defaultFacts = List.of(
                "Honey never spoils.",
                "Octopuses have three hearts.",
                "Bananas are curved because they grow towards the sun."
        );

        public String name = defaultName;
        public List<String> messages = defaultMessages;
        public int delay = defaultDelay;

        // New fields
        public boolean enableRandomFacts = defaultEnableRandomFacts;
        public String factPrefix = defaultFactPrefix;
        public List<String> facts = new ArrayList<>(defaultFacts);

        public Iterator<String> getMessages() {
            if (enableRandomFacts) {
                List<String> validFacts = facts.stream()
                        .filter(f -> f != null && !f.trim().isEmpty())
                        .toList();

                if (!validFacts.isEmpty()) {
                    String chosenFact = validFacts.get(ThreadLocalRandom.current().nextInt(validFacts.size()));
                    String fullMessage = (factPrefix.isBlank() ? "" : factPrefix.trim() + " ") + chosenFact.trim();

                    // Clamp to 256 characters to avoid vanilla disconnect packets
                    if (fullMessage.length() > 256) {
                        fullMessage = fullMessage.substring(0, 256);
                    }
                    return List.of(fullMessage).iterator();
                }
            }
            return messages.iterator();
        }

        public int getDelay() {
            return delay;
        }
    }
}