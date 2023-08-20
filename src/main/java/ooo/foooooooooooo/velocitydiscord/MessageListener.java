package ooo.foooooooooooo.velocitydiscord;

import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import ooo.foooooooooooo.velocitydiscord.util.StringTemplate;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class MessageListener extends ListenerAdapter {
  private static final Pattern WEBHOOK_ID_REGEX = Pattern.compile("^https://discord\\.com/api/webhooks/(\\d+)/.+$");
  private final String webhookId;
  private final ProxyServer server;
  private final Logger logger;
  private final Config config;
  private JDA jda;

  public MessageListener(ProxyServer server, Logger logger, Config config) {
    this.server = server;
    this.logger = logger;
    this.config = config;

    final var matcher = WEBHOOK_ID_REGEX.matcher(config.WEBHOOK_URL);
    this.webhookId = matcher.find() ? matcher.group(1) : null;
    logger.log(Level.FINER, "Found webhook id: {0}", webhookId);
  }

  @Override
  public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
    if (!event.isFromType(ChannelType.TEXT)) {
      logger.finest("ignoring non text channel message");
      return;
    }

    if (jda == null) {
      jda = event.getJDA();
    }

    var channel = event.getChannel().asTextChannel();
    if (!channel.getId().equals(config.CHANNEL_ID)) {
      return;
    }

    var author = event.getAuthor();
    if (!config.SHOW_BOT_MESSAGES && author.isBot()) {
      logger.finer("ignoring bot message");
      return;
    }

    if (author.getId().equals(jda.getSelfUser().getId()) || (Objects.nonNull(this.webhookId) && author.getId().equals(this.webhookId))) {
      logger.finer("ignoring own message");
      return;
    }

    var message = event.getMessage();
    var guild = event.getGuild();

    var member = guild.getMember(author);
    if (member == null) {
      logger.warning("failed to get member: " + author.getId());
      return;
    }

    var color = member.getColor();
    if (color == null) {
      color = Color.white;
    }
    var hex = "#" + Integer.toHexString(color.getRGB()).substring(2);

    // parse configured message formats
    var discord_chunk = new StringTemplate(config.DISCORD_CHUNK).add("discord_color", config.DISCORD_COLOR).toString();

    var username_chunk = new StringTemplate(config.USERNAME_CHUNK)
      .add("role_color", hex)
      .add("username", author.getName())
      .add("discriminator", author.getDiscriminator())
      .add("nickname", member.getEffectiveName())
      .toString();

    var attachment_chunk = config.ATTACHMENTS;
    var message_chunk = new StringTemplate(config.MC_CHAT_MESSAGE)
      .add("discord_chunk", discord_chunk)
      .add("username_chunk", username_chunk)
      .add("message", message.getContentDisplay());

    var attachmentChunks = new ArrayList<String>();

    List<Message.Attachment> attachments = new ArrayList<>();
    if (config.SHOW_ATTACHMENTS) {
      attachments = message.getAttachments();
    }

    for (var attachment : attachments) {
      attachmentChunks.add(new StringTemplate(attachment_chunk).add("url", attachment.getUrl()).add("attachment_color", config.ATTACHMENT_COLOR).toString());
    }

    var content = message.getContentDisplay();

    // Remove leading whitespace from attachments if there's no content
    if (content.isBlank()) {
      message_chunk = message_chunk.replace(" {attachments}", "{attachments}");
    }

    message_chunk.add("message", content);
    message_chunk.add("attachments", String.join(" ", attachmentChunks));

    sendMessage(MiniMessage.miniMessage().deserialize(message_chunk.toString()).asComponent());
  }

  private void sendMessage(Component msg) {
    for (var server : server.getAllServers()) {
      server.sendMessage(msg);
    }
  }
}
