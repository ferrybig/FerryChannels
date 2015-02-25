/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferry.bukkit.plugins.ferrychannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.bukkit.configuration.ConfigurationSection;

/**
 *
 * @author Fernando
 */
public final class PlayerInfo
{
	private final String nameColor;

	public PlayerInfo(Collection<String> channels, int mainChannel, String chatTag, String nameColor)
	{
		this.channels.addAll(channels);
		this.mainChannel = mainChannel;
		this.chatTag = chatTag;
		this.nameColor = nameColor;
	}
	/**
	 * Thread safe and fast way to read out the channels
	 */
	private final List<String> channels = new CopyOnWriteArrayList<String>();
	private volatile int mainChannel = -1;
	private final String chatTag;

	public String getNameColor()
	{
		return nameColor;
	}

	/**
	 * Gets the chat tag of courent player
	 * @return
	 */
	public String getChatTag()
	{
		return chatTag;
	}

	public List<String> getChannels()
	{
		return channels;
	}

	public int getMainChannel()
	{
		return mainChannel;
	}

	public void setMainChannels(int mainChannel)
	{
		this.mainChannel = mainChannel;
	}

	public void save(ConfigurationSection to)
	{
		this.save(to, true);
	}

	public void save(ConfigurationSection to, boolean fullSave)
	{
		to.set("mainChannel", mainChannel);
		to.set("channels", new LinkedList<String>(this.channels));
		if (fullSave)
		{
			to.set("chattag", this.chatTag);
			to.set("nameColor", nameColor);
		}
	}

	public static PlayerInfo load(ConfigurationSection from)
	{
		String chattag = from.getString("chattag", "");
		int mainChannel = from.getInt("mainChannel", -1);
		Collection<String> channels = from.getStringList("channels");
		String nameColor = from.getString("nameColor", "");
		return new PlayerInfo(channels, mainChannel, chattag, nameColor);
	}

	public static PlayerInfo load(ConfigurationSection from, PlayerInfo defaultInfo)
	{
		if (defaultInfo == null)
		{
			return load(from);
		}

		String chattag = from.getString("chattag", defaultInfo.getChatTag());
		int mainChannel = from.getInt("mainChannel", defaultInfo.getMainChannel());
		Collection<String> channels = from.getStringList("channels");
		if (!from.contains("channels"))
		{
			channels = defaultInfo.getChannels();
		}
		String nameColor = from.getString("nameColor", defaultInfo.getNameColor());
		return new PlayerInfo(channels, mainChannel, chattag, nameColor);

	}

	@Override
	public PlayerInfo clone()
	{
		return new PlayerInfo(this.channels, mainChannel, chatTag, nameColor);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		final PlayerInfo other = (PlayerInfo) obj;
		if (this.channels != other.channels && (this.channels == null || !this.channels.equals(other.channels)))
		{
			return false;
		}
		if (this.mainChannel != other.mainChannel)
		{
			return false;
		}
		if ((this.chatTag == null) ? (other.chatTag != null) : !this.chatTag.equals(other.chatTag))
		{
			return false;
		}
		return true;
	}

	@Override
	public int hashCode()
	{
		int hash = 5;
		hash = 23 * hash + (this.channels != null ? this.channels.hashCode() : 0);
		hash = 23 * hash + this.mainChannel;
		hash = 23 * hash + (this.chatTag != null ? this.chatTag.hashCode() : 0);
		return hash;
	}

	@Override
	public String toString()
	{
		return "PlayerInfo{" + "channels=" + channels + ", mainChannel=" + mainChannel + ", chatTag=" + chatTag + '}';
	}
}
