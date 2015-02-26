package me.ferry.bukkit.plugins.ferrychannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import me.ferry.bukkit.plugins.PluginBase;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

public class FerryChatChannelPlugin extends PluginBase implements Listener, PluginMessageListener {

    public final Map<String, PlayerInfo> players = Collections.synchronizedMap(new HashMap<String, PlayerInfo>());
    public final Map<String, PlayerInfo> offlinePlayers = Collections.synchronizedMap(new HashMap<String, PlayerInfo>());
    public final Map<String, PlayerInfo> offlinePlayerToGroups = Collections.synchronizedMap(new HashMap<String, PlayerInfo>());
    public final Map<String, PlayerInfo> groups = Collections.synchronizedMap(new HashMap<String, PlayerInfo>());
    public final Map<?, ?>[] toClear = new Map<?, ?>[]{
        players, offlinePlayers, offlinePlayerToGroups, groups
    };
    private static PlayerInfo defaultInfoUnconfigured = new PlayerInfo(null, -1, null, null);
    private PlayerInfo defaultInfo;
    private String recieveFormat;
    private String messageFormat;
    /**
     * The channels where speak is blocked Only writen on plugin startup and
     * need to be thread safe, <code>CopyOnWriteArraySet</code> is a good choose
     * for this
     */
    private final Set<String> blockedChannels = new CopyOnWriteArraySet<>();
    /**
     * You're joined at login to these channels Only writen on plugin startup
     * and need to be thread safe, <code>CopyOnWriteArraySet</code> is a good
     * choose for this
     */
    private final Set<String> defaultChannels = new CopyOnWriteArraySet<>();
    /**
     * Channels without join and leave broadcast Only writen on plugin startup
     * and need to be thread safe, <code>CopyOnWriteArraySet</code> is a good
     * choose for this
     */
    private final Set<String> preventChannelBroadcasts = new CopyOnWriteArraySet<>();
    /**
     * the channels where death messages are moved to Only writen on plugin
     * startup and need to be thread safe, <code>CopyOnWriteArraySet</code> is a
     * good choose for this
     */
    private final Set<String> deathChannels = new CopyOnWriteArraySet<>();
    private final List<String> channelColors = new CopyOnWriteArrayList<>();
    private String channelLeaveMessage;
    private String channelJoinMessage;
    private boolean handleDeathMessages;
    private String deathFormat;
    private boolean stopTalk = true;
    private boolean debug = false;
    private Messages messages;
    private boolean setDisplayName;
    private int maxChannels;
    private int maxLengthAllowed;
    private boolean blockAllChannelBroadcasts;
    private boolean dontSavePlayers;

    @Override
    public void onPluginLoad() {
        this.useMetrics();
        this.useConfig();
    }

    @Override
    public void onPluginEnable() {
        FileConfiguration config = this.getConfig();
        config.options().header("");
        boolean mustRegenConfig = config.getBoolean("regenerateConfig", true);
        if (mustRegenConfig) {
            this.saveDefaultConfig();
            this.reloadConfig();
            config = getConfig();
        }
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "FerryChatChannel", this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "FerryChatChannel");
        new BukkitRunnable() {

            @Override
            public void run() {
                for (Map.Entry<String, PlayerInfo> offline : offlinePlayers.entrySet()) {
                    savePlayerInfo(offline.getKey(), offline.getValue());
                }
                offlinePlayers.clear();
            }
        }.runTaskTimer(this, 200, 200);

        loadPlugin(config);
    }

    @Override
    public void onPluginDisable() {
        this.unloadPlugin();
    }

    @Override
    public void onPluginCleanup() {
        for (Map<?, ?> tmp : toClear) {
            tmp.clear();
        }
    }

    private void registerCommands() {
        CommandExecutor channelSelectCommands = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] arg) {
                FerryChatChannelPlugin.this.selectChannelAndChat(sender, Integer.parseInt(cmd.getName()), arg);
                return true;
            }
        };
        int i = 0;
        PluginCommand command;
        while ((command = this.getCommand(String.valueOf(i))) != null) {
            command.setExecutor(channelSelectCommands);
            i++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent evt) {
        loadPlayer(evt.getPlayer(), true);
    }

    private List<String> getGroups(Player player) {
        List<String> groups = new ArrayList<>();
        if (player.hasMetadata("groups")) {
            List<MetadataValue> metadata = player.getMetadata("groups");
            for (MetadataValue value : metadata) {
                Object value1 = value.value();
                if (value1 instanceof Iterable<?>) {
                    for (Object obj : (Iterable) value1) {
                        groups.add(obj.toString());
                    }
                }
            }
        }
        Set<PermissionAttachmentInfo> perms = player.getEffectivePermissions();
        PlayerInfo groupInfo = this.defaultInfo;
        for (PermissionAttachmentInfo perm : perms) {
            if (perm.getPermission().startsWith("group.") && perm.getValue()) {
                String group = perm.getPermission().substring(6);
                groups.add(group);
            }
        }
        return groups;
    }

    private PlayerInfo getSuperPermGroup(Player player) {

        for (String obj : getGroups(player)) {

            if (this.groups.containsKey(obj)) {
                return this.groups.get(obj);
            }
        }
        return defaultInfo.clone();
    }

    public void broadcastChannelJoin(Player player, String channel) {
        if (this.stopTalk) {
            return;
        }
        if (this.blockAllChannelBroadcasts) {
            return;
        }
        if (!this.preventChannelBroadcasts.contains(channel)) {
            broadcastChat(new ArrayList<>(Bukkit.getOnlinePlayers()), channel, player, this.channelJoinMessage.replace(CHANNEL_PATTERN, channel), "join");
        }
    }
    public static final String CHANNEL_PATTERN = "{channel}";

    public void broadcastChannelQuit(Player player, String channel) {
        if (this.stopTalk) {
            return;
        }
        if (this.blockAllChannelBroadcasts) {
            return;
        }
        if (!this.preventChannelBroadcasts.contains(channel)) {
            broadcastChat(new ArrayList<>(Bukkit.getOnlinePlayers()), channel, player, this.channelLeaveMessage.replace(CHANNEL_PATTERN, channel), "leave");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent evt) {
        final String playerName = evt.getPlayer().getName();

        if (this.players.containsKey(playerName)) {
            PlayerInfo info = this.players.get(playerName);
            this.offlinePlayers.put(playerName, this.players.get(playerName));
            this.players.remove(playerName);

            for (String channel : info.getChannels()) {
                broadcastChannelQuit(evt.getPlayer(), channel);
            }
        }
    }

    public String getChannelColor(int color) {
        if (this.channelColors.isEmpty()) {
            return ChatColor.WHITE.toString();
        }
        if (color < 0) {
            return ChatColor.WHITE.toString();
        }
        int colorSize = this.channelColors.size();
        while (color >= colorSize) {
            color -= colorSize;
        }
        return this.channelColors.get(color);
    }

    public String getChannelName(List<String> list, int index) {
        if (index < 0) {
            return null;
        }
        if (index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /**
     * handles chat
     * <p>
     * @param evt chat event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent evt) {
        if (evt.isCancelled()) {
            return;
        }
        final Player player = evt.getPlayer();
        final String playerName = player.getName();
        final PlayerInfo info = this.players.get(playerName);
        if (info == null) {
            sendMessage(player, ChatColor.RED + "Something went wrong, :'(");
            return;
        }
        final List<String> channels = info.getChannels();
        final int mainChannel = info.getMainChannel();
        final String chatTag = info.getChatTag() == null ? "[]" : info.getChatTag();
        String channelName = getChannelName(channels, mainChannel);
        String format = this.messageFormat.replace("{tag}", ChatColor.translateAlternateColorCodes('&', chatTag)).
            replace("{name}", playerName);

        final Set<Player> recievers = evt.getRecipients();

        if (evt.getPlayer().hasPermission("channel.color")) {
            evt.setMessage(ChatColor.translateAlternateColorCodes('&', evt.getMessage()));
        }
        if (channels.isEmpty()) {
            evt.setCancelled(true);
            player.sendMessage(this.messages.channelNoJoinedMessage());
        } else if (mainChannel == -1 || mainChannel >= channels.size()) {
            info.setMainChannels(0);
            this.scheduleSave();
            channelName = getChannelName(channels, 0);
            format = format.replace(CHANNEL_PATTERN, channelName);
            if (this.blockedChannels.contains(channelName)) {
                evt.setCancelled(true);
                player.sendMessage(this.messages.channelProtectedMessage(channelName, 0, this.getChannelColor(channels.indexOf(channelName))));
                return;
            }
            broadcastChat(recievers, channelName, player, format, evt.getMessage());
        } else {
            format = format.replace(CHANNEL_PATTERN, channelName);
            if (this.blockedChannels.contains(channelName)) {
                evt.setCancelled(true);
                player.sendMessage(this.messages.channelProtectedMessage(channelName, mainChannel, this.getChannelColor(channels.indexOf(channelName))));
                return;
            }
            broadcastChat(recievers, channelName, player, format, evt.getMessage());
        }
        recievers.clear();

        evt.setFormat(ChatColor.translateAlternateColorCodes('&', "&2" + channelName + "&f: " + info.getNameColor() + playerName + "&f: %2$s"));

    }

    public void broadcastChat(Collection<? extends Player> playerList, String chatChannel, Player player, String format, String message) {
        final String playerName = player != null ? player.getName() : "";
        PlayerInfo info1 = this.players.get(playerName);
        String lowFormat = this.recieveFormat.replace("{format}", format).
            replace("{name}", playerName).
            replace("{dispname}", player.getDisplayName()).
            replace(CHANNEL_PATTERN, chatChannel).
            replace("{nameColor}", info1 != null ? ChatColor.translateAlternateColorCodes('&', info1.getNameColor()) : "");
        for (Player toTest : playerList) {
            PlayerInfo info = this.players.get(toTest.getName());
            if (info != null) {
                final List<String> channels = info.getChannels();
                int i = -1;
                for (String channel : channels) {
                    i++;
                    if (channel.equals(chatChannel)) {
                        toTest.sendMessage(lowFormat.replace("{channelindex}", String.valueOf(i)).
                            replace("{color}", getChannelColor(i)).
                            replace("{message}", message));
                    }
                }
            }
        }
    }
    BukkitRunnable task;

    private void refreshTask() {
        this.task = this.createTask();
    }

    public void scheduleSave() {
        if (this.task == null) {
            refreshTask();
            this.task.runTaskLater(this, 6000);

        }
    }

    private BukkitRunnable createTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                FerryChatChannelPlugin.this.task = null;
                FerryChatChannelPlugin.this.save();
                FerryChatChannelPlugin.this.saveConfig();
            }
        };
    }

    private void save() {
        for (Map.Entry<String, PlayerInfo> offline : this.offlinePlayers.entrySet()) {
            savePlayerInfo(offline.getKey(), offline.getValue());
        }
        this.offlinePlayers.clear();
        for (Map.Entry<String, PlayerInfo> offline : this.players.entrySet()) {
            savePlayerInfo(offline.getKey(), offline.getValue());
            this.offlinePlayerToGroups.remove(offline.getKey());
        }
        logInfo("AutoSaved!");
    }

    private void savePlayerInfo(String name, PlayerInfo info) {
        if (this.dontSavePlayers) {
            return;
        }
        ConfigurationSection playerSection = this.getConfig().getConfigurationSection("players." + name);
        Player player = Bukkit.getPlayerExact(name);
        PlayerInfo compare = player == null ? this.offlinePlayerToGroups.get(name) : this.getSuperPermGroup(player);
        if (info.equals(compare)) {
            if (playerSection == null) {
                return;
            }
        }
        info.save(playerSection == null ? this.getConfig().createSection("players." + name) : playerSection);
    }

    private void refreshPlayer(Player name) {
        if (this.players.containsKey(name.getName())) {
            this.players.remove(name.getName());
            this.loadPlayer(name, false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.testPermission(sender)) {
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("chat-reload")) {
            sender.sendMessage("Reloading....");
            if (this.task != null) {
                sender.sendMessage("Ignoring pending save....");
                this.task.cancel();
                this.task = null;
            }
            this.unloadPlugin();
            this.onPluginEnable();
        } else if (cmd.getName().equalsIgnoreCase("chat-debug")) {
            sender.sendMessage("Groups: " + String.valueOf(this.getGroups(sender instanceof Player ? (Player) sender : null)));
            sender.sendMessage("PlayerInfo: " + String.valueOf(this.getSuperPermGroup(sender instanceof Player ? (Player) sender : null)));
        } else if (cmd.getName().equalsIgnoreCase("join")) {
            if (args.length == 1) {
                this.joinChat(sender, args[0]);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("leave")) {
            if (args.length == 1) {
                this.leaveChat(sender, args[0]);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("channels")) {
            this.showChats(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("ferry-channels")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reloadPlayer")) {
                }
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent evt) {
        if (!this.handleDeathMessages) {
            return;
        }
        String deathMsg = evt.getDeathMessage();
        evt.setDeathMessage(null);
        for (String channel : this.deathChannels) {
            this.broadcastChat(new ArrayList<Player>(Bukkit.getOnlinePlayers()), channel, evt.getEntity(), this.deathFormat.replace(CHANNEL_PATTERN, channel), deathMsg);
        }
        logInfo("Death message: " + deathMsg);
    }

    private void selectChannelAndChat(CommandSender sender, int newMainChannel, String[] arg) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            PlayerInfo info = this.players.get(player.getName());
            if (newMainChannel >= info.getChannels().size()) {
                sender.sendMessage(this.messages.channelNumberToHigh(info.getChannels().size()));
            }
            info.setMainChannels(newMainChannel);
            this.scheduleSave();
            final List<String> modifedChannelList = new ArrayList<String>(info.getChannels());
            String channelName = getChannelName(modifedChannelList, info.getMainChannel());
            sender.sendMessage(this.messages.chancedDefaultTalkChannel(channelName, newMainChannel, this.getChannelColor(newMainChannel)));
            if (arg.length > 0) {
                StringBuilder build = new StringBuilder(arg.length * 13);
                for (String txt : arg) {
                    build.append(txt).append(" ");
                }
                player.chat(build.toString().trim());
            }
        } else {
            sender.sendMessage(this.messages.playerOnly());
        }
    }

    private void joinChat(CommandSender sender, String newName) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            PlayerInfo info = this.players.get(player.getName());
            for (char character : newName.toCharArray()) {
                if (!Character.isLetterOrDigit(character)) {
                    player.sendMessage(this.messages.invalidChannelName(newName));
                    return;
                }
            }
            if (newName.length() > this.maxLengthAllowed) {
                player.sendMessage(this.messages.tooLongName());
                return;
            }
            if (info.getChannels().contains(newName)) {
                player.sendMessage(this.messages.alreadyJoined(newName));
                return;
            }
            if (info.getChannels().size() > this.maxChannels) {
                player.sendMessage(this.messages.channelNumberToHigh(maxChannels));
                return;
            }
            this.broadcastChannelJoin(player, newName);
            int index = info.getChannels().size();
            info.getChannels().add(newName);
            this.scheduleSave();
            player.sendMessage(this.messages.joinDone(newName, index, this.getChannelColor(index)));
        } else {
            sender.sendMessage(this.messages.playerOnly());
        }
    }

    private void leaveChat(CommandSender sender, String newName) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            PlayerInfo info = this.players.get(player.getName());
            if (!info.getChannels().contains(newName)) {
                player.sendMessage(this.messages.leaveChannelNotFound(getPluginName()));
                return;
            }
            info.getChannels().remove(newName);
            this.scheduleSave();
            player.sendMessage(this.messages.leaveDone(newName));
            this.broadcastChannelQuit(player, newName);
        } else {
            sender.sendMessage(this.messages.playerOnly());
        }
    }

    private void showChats(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            PlayerInfo info = this.players.get(player.getName());
            final List<String> channels = info.getChannels();
            String header = this.messages.listHeader();
            if (!header.isEmpty()) {
                player.sendMessage(header);
            }
            int i = -1;
            String mainChannel = null;
            String mainChannelColor = null;
            int mainChannelIndex = -1;
            for (String channel : channels) {
                i++;
                if (i == info.getMainChannel()) {
                    mainChannel = channel;
                    mainChannelColor = this.getChannelColor(i);
                    mainChannelIndex = i;
                }
                player.sendMessage(this.messages.listBody(channel, i, this.getChannelColor(i)));
            }
            String footer = this.messages.listFooter();
            if (!footer.isEmpty()) {
                player.sendMessage(footer);
            }
            if (mainChannel != null) {
                player.sendMessage(this.messages.listMainChannel(mainChannel, mainChannelIndex, mainChannelColor));
            }
        } else {
            sender.sendMessage(this.messages.playerOnly());
        }
    }

    private void loadGroups() {
        FileConfiguration config = this.getConfig();
        ConfigurationSection groupConfig = config.getConfigurationSection("groups");
        if (groupConfig == null) {
            groupConfig = config.createSection("groups");
        }
        for (String key : groupConfig.getKeys(false)) {
            this.groups.put(key, PlayerInfo.load(groupConfig.getConfigurationSection(key), this.defaultInfo));
        }
    }

    public void reload() {
        PluginManager pm = Bukkit.getPluginManager();
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        pm.disablePlugin(this);
        pm.enablePlugin(this);
    }

    @Override
    public void onPluginMessageReceived(String string, Player player, byte[] bytes) {
        if (player.hasMetadata("muted")) {
            List<MetadataValue> metadata = player.getMetadata("muted");
            for (MetadataValue value : metadata) {
                boolean muted = value.asBoolean();
                if (muted) {
                    return;
                }
            }
        }
    }

    private void loadPlugin(FileConfiguration config) {
        this.messages = new Messages(this);

        this.registerCommands();

        this.loadGroups();

        this.defaultInfo = PlayerInfo.load(config.getConfigurationSection("defaultInfo"), defaultInfoUnconfigured);
        this.defaultInfo.save(config.createSection("defaultInfo"));
        /**
         * per-player messages {channelindex} = channel index {color} = channel
         * color {format} = channel mesage
         */
        this.recieveFormat = ChatColor.translateAlternateColorCodes('&', config.getString("channel.recieveformat", "{color} [{channelindex}] &r {format}"));
        config.set("channel.recieveformat", this.recieveFormat.replace(ChatColor.COLOR_CHAR, '&'));

        /**
         * Channel message {tag} = chattag {name} = playername {message} =
         * message {channel} = channel name {nameColor} = name color
         */
        this.messageFormat = ChatColor.translateAlternateColorCodes('&', config.getString("channel.messageformat", "[{channel}] [{tag} <{nameColor}{name}>] {message}"));
        config.set("channel.messageformat", this.messageFormat.replace(ChatColor.COLOR_CHAR, '&'));

        this.channelJoinMessage = ChatColor.translateAlternateColorCodes('&', config.getString("channel.joinMsg", "{name} has joined this channel"));
        config.set("channel.joinMsg", this.channelJoinMessage.replace(ChatColor.COLOR_CHAR, '&'));

        this.channelLeaveMessage = ChatColor.translateAlternateColorCodes('&', config.getString("channel.leaveMsg", "{name} has left this channel"));
        config.set("channel.leaveMsg", this.channelLeaveMessage.replace(ChatColor.COLOR_CHAR, '&'));

        this.deathFormat = ChatColor.translateAlternateColorCodes('&', config.getString("channel.deathformat", "[{channel}] {message}"));
        config.set("channel.deathformat", this.deathFormat.replace(ChatColor.COLOR_CHAR, '&'));

        this.handleDeathMessages = config.getBoolean("death.enabled");
        config.set("death.enabled", handleDeathMessages);

        this.debug = config.getBoolean("debug", false);
        config.set("debug", debug);

        this.blockAllChannelBroadcasts = config.getBoolean("preventBroadcastAll", false);
        config.set("preventBroadcastAll", blockAllChannelBroadcasts);

        this.dontSavePlayers = config.getBoolean("dontSavePlayers", false);
        config.set("dontSavePlayers", dontSavePlayers);

        this.setDisplayName = config.getBoolean("setDisplayName", false);
        config.set("setDisplayName", this.setDisplayName);

        this.maxChannels = config.getInt("channels.limit", 10);

        this.maxLengthAllowed = config.getInt("channels.nameLimit", 20);

        this.blockedChannels.clear();
        this.blockedChannels.addAll(config.getStringList("blockedChannels"));

        this.defaultChannels.clear();
        this.defaultChannels.addAll(config.getStringList("defaultChannels"));

        this.preventChannelBroadcasts.clear();
        this.preventChannelBroadcasts.addAll(config.getStringList("preventChannelBroadcasts"));

        this.deathChannels.clear();
        this.deathChannels.addAll(config.getStringList("death.channels"));

        this.channelColors.clear();
        List<String> colorList = config.getStringList("channelColors");
        for (String color : colorList) {
            this.channelColors.add(ChatColor.translateAlternateColorCodes('&', color));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.onJoin(new PlayerJoinEvent(player, "plugin starting"));
        }
        this.stopTalk = false;
    }

    private void unloadPlugin() {
        this.stopTalk = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.onQuit(new PlayerQuitEvent(player, "plugin stopping"));
        }
        if (this.task != null) {
            this.task.run();
        }
        this.players.clear();
        this.offlinePlayerToGroups.clear();
        this.offlinePlayers.clear();
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "FerryChatChannel", this);
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "FerryChatChannel");
    }

    private void loadPlayer(Player evt, boolean useCache) {
        final String playerName = evt.getName();
        PlayerInfo info = null;
        if (offlinePlayers.containsKey(playerName)) {
            if (useCache) {
                players.put(playerName, info = offlinePlayers.get(playerName));
            }
            offlinePlayers.remove(playerName);
        }
        if (info == null) {
            ConfigurationSection playerSection = this.getConfig().getConfigurationSection("players." + playerName);
            PlayerInfo group = getSuperPermGroup(evt);
            if (playerSection == null) {
                if (group == null) {
                    group = this.defaultInfo.clone();
                }
                players.put(playerName, info = group.clone());

            } else {
                players.put(playerName, info = PlayerInfo.load(playerSection, group));
            }
            this.offlinePlayerToGroups.put(playerName, group);
        }
        for (String str : this.defaultChannels) {
            if (!info.getChannels().contains(str)) {
                info.getChannels().add(str);
            }
        }
        StringBuilder channelStr = new StringBuilder();
        for (String channel : info.getChannels()) {
            broadcastChannelJoin(evt, channel);
            channelStr.append(channel).append(",");
        }
        if (this.setDisplayName) {
            evt.setDisplayName(ChatColor.translateAlternateColorCodes('&', info.getNameColor() + evt.getPlayer().getName()));
        }
        //evt.sendPluginMessage(this, "FerryChatChannel", ("channels:" + channelStr.toString()).getBytes());
    }
}
