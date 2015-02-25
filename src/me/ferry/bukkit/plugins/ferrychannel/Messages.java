/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.ferry.bukkit.plugins.ferrychannel;

import org.bukkit.ChatColor;

/**
 *
 * @author Fernando
 */
public class Messages
{
	final FerryChatChannelPlugin plugin;

	public Messages(FerryChatChannelPlugin plugin)
	{
		this.plugin = plugin;
	}

	public String playerOnly()
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.playerOnly", "Sorry, this plugin is meant for players only!"));
	}

	public String invalidChannelName(String name)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.invalidChannelName", "You have specified a invalid channel name!").
			replace("{channel}", name));
	}

	public String leaveChannelNotFound(String name)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.leaveChannelNotFound", "You cant leave channel {channel} because you aren't inside it!").
			replace("{channel}", name));
	}

	public String leaveDone(String name)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.leaveDone", "You left channel {channel}!").
			replace("{channel}", name));
	}public String alreadyJoined(String name)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.alreadyJoined", "You already joined channel {channel}!").
			replace("{channel}", name));
	}

	public String tooManyChannels()
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.tooManyChannels", "You have reached the maximum limit of channels!"));
	}public String tooLongName()
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.channelNameTooLong", "There is a limit on channel lengths!"));
	}

	public String channelNumberToHigh(int higestChannelNumber)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.channel_number_to_high", "You dont have a channel whit that number on your list!").
			replace("{max}", String.valueOf(higestChannelNumber)));
	}

	public String channelNoJoinedMessage()
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.channelNoJoined", "Your are not talking inside a chat channel, try /join global"));
	}

	public String channelProtectedMessage(String channelName, int channelNumber, String color)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.channelProtected", "Channel {color}{id} - {name}&f is protected from messages").
			replace("{id}", String.valueOf(channelNumber)).
			replace("{name}", channelName).
			replace("{color}", color));
	}

	public String joinDone(String channelName, int channelNumber, String color)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.joinDone", "SuccesFully joined channel {color}{id} - {name}").
			replace("{id}", String.valueOf(channelNumber)).
			replace("{name}", channelName).
			replace("{color}", color));
	}

	public String chancedDefaultTalkChannel(String newChannel, int newNumber, String color)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.chancedDefaultTalkChannel",
												     "You set channel {color}{id} - {name}&f as your default chat channel").
			replace("{id}", String.valueOf(newNumber)).
			replace("{name}", newChannel).
			replace("{color}", color));

	}

	public String listHeader()
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.chatList.header", ""));
	}

	public String listBody(String channelName, int channelNumber, String color)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.chatList.body", "{color}{id} &f- {color}{name}").
			replace("{id}", String.valueOf(channelNumber)).
			replace("{name}", channelName).
			replace("{color}", color));
	}

	public String listFooter()
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.chatList.footer", ""));
	}

	public String listMainChannel(String channelName, int channelNumber, String color)
	{
		return ChatColor.translateAlternateColorCodes('&', this.plugin.getConfig().getString("messages.chatList.body", "You're default channel is: {color}{id} &f- {color}{name}").
			replace("{id}", String.valueOf(channelNumber)).
			replace("{name}", channelName).
			replace("{color}", color));
	}
}
