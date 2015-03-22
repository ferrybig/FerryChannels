/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferry.bukkit.plugins.ferrychannel;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Fernando
 */
public final class PlayerInfo {

    private final String nameColor;

    public PlayerInfo(Collection<String> channels, int mainChannel, String chatTag, String nameColor) {
        this(null, channels, mainChannel, chatTag, nameColor);
    }

    public PlayerInfo(PlayerInfo parent, Collection<String> channels, int mainChannel, String chatTag, String nameColor) {
        if (this.channels != null) {
            this.channels.addAll(channels);
        } else if (parent != null) {
            this.channels.addAll(parent.getChannels());
        }
        this.mainChannel = mainChannel;
        this.chatTag = chatTag;
        this.nameColor = nameColor;
        this.parent = parent;
    }
    private final PlayerInfo parent;
    /**
     * Thread safe and fast way to read out the channels
     */
    private final List<String> channels = new CopyOnWriteArrayList<>();
    private volatile int mainChannel = -1;
    private final String chatTag;

    public String getNameColor() {
        return nameColor != null ? nameColor : parent.nameColor;
    }

    /**
     * Gets the chat tag of courent player
     *
     * @return
     */
    public String getChatTag() {
        return chatTag != null ? chatTag : parent.chatTag;
    }

    public List<String> getChannels() {
        return channels;
    }

    public int getMainChannel() {
        return mainChannel;
    }

    public void setMainChannels(int mainChannel) {
        this.mainChannel = mainChannel;
    }

    public void save(ConfigurationSection to) {
        to.set("mainChannel", mainChannel < 0 ? null : mainChannel);
        to.set("channels", parent.channels.equals(this.channels) ? null : new LinkedList<>(channels));
        to.set("chattag", chatTag);
        to.set("nameColor", nameColor);
    }

    public static PlayerInfo load(ConfigurationSection from) {
        return load(from, null);
    }

    public static PlayerInfo load(ConfigurationSection from, PlayerInfo parent) {
        String chattag = from.getString("chattag", "");
        int mainChannel = from.getInt("mainChannel", -1);
        Collection<String> channels = from.getStringList("channels");
        String nameColor = from.getString("nameColor", "");
        return new PlayerInfo(parent, channels, mainChannel, chattag, nameColor);
    }

    @Override
    public PlayerInfo clone() {
        return new PlayerInfo(parent, channels, mainChannel, chatTag, nameColor);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PlayerInfo other = (PlayerInfo) obj;
        if (!Objects.equals(this.nameColor, other.nameColor)) {
            return false;
        }
        if (!Objects.equals(this.parent, other.parent)) {
            return false;
        }
        if (!Objects.equals(this.channels, other.channels)) {
            return false;
        }
        if (this.mainChannel != other.mainChannel) {
            return false;
        }
        if (!Objects.equals(this.chatTag, other.chatTag)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 23 * hash + Objects.hashCode(this.nameColor);
        hash = 23 * hash + Objects.hashCode(this.parent);
        hash = 23 * hash + Objects.hashCode(this.channels);
        hash = 23 * hash + this.mainChannel;
        hash = 23 * hash + Objects.hashCode(this.chatTag);
        return hash;
    }

    @Override
    public String toString() {
        return "PlayerInfo{" + "nameColor=" + nameColor + ", parent=" + parent + ", channels=" + channels + ", mainChannel=" + mainChannel + ", chatTag=" + chatTag + '}';
    }

}
