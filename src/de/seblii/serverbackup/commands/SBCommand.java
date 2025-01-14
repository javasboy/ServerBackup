package de.seblii.serverbackup.commands;

import com.google.common.io.Files;
import de.seblii.serverbackup.BackupManager;
import de.seblii.serverbackup.utils.DropboxManager;
import de.seblii.serverbackup.utils.FtpManager;
import de.seblii.serverbackup.ServerBackup;
import de.seblii.serverbackup.ZipManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class SBCommand implements CommandExecutor, TabCompleter {

	private ServerBackup backup = ServerBackup.getInstance();

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		if (sender.hasPermission("backup.admin")) {
			if (args.length == 1) {
				if (args[0].equalsIgnoreCase("shutdown")) {
					if (backup.shutdownProgress) {
						backup.shutdownProgress = false;

						sender.sendMessage(backup.processMessage("Command.Shutdown.Cancel"));
					} else {
						ServerBackup.getInstance().shutdownProgress = true;

						sender.sendMessage(backup.processMessage("Command.Shutdown.Start"));
					}
				} else if (args[0].equalsIgnoreCase("list")) {
					Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
						File[] backups = new File(ServerBackup.getInstance().backupDestination).listFiles();

						if (backups.length == 0
								|| backups.length == 1 && backups[0].getName().equalsIgnoreCase("Files")) {
							sender.sendMessage(backup.processMessage("Error.NoBackups"));

							return;
						}

						Arrays.sort(backups);

						if ((backups.length - 1) < 10) {
							sender.sendMessage(
									"----- Backup 1-" + (backups.length - 1) + "/" + (backups.length - 1) + " -----");
						} else {
							sender.sendMessage("----- Backup 1-10/" + (backups.length - 1) + " -----");
						}
						sender.sendMessage("");

						for (int i = 0; i < (backups.length - 1) && i < 10; i++) {
							if (backups[i].getName().equalsIgnoreCase("Files")) {
								i--;
								continue;
							}

							double fileSize = (double) FileUtils.sizeOf(backups[i]) / 1000 / 1000;
							fileSize = Math.round(fileSize * 100.0) / 100.0;

							if (sender instanceof Player) {
								Player p = (Player) sender;

								TextComponent msg = new TextComponent("§7[" + Integer.valueOf(i + 1) + "] §r"
										+ backups[i].getName() + " §7[" + fileSize + "MB]");
								msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
										new ComponentBuilder("Click to get Backup name").create()));
								msg.setClickEvent(
										new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, backups[i].getName()));

								p.spigot().sendMessage(msg);
							} else {
								sender.sendMessage(backups[i].getName());
							}
						}

						int maxPages = (backups.length - 1) / 10;

						if ((backups.length - 1) % 10 != 0) {
							maxPages++;
						}

						sender.sendMessage("");
						sender.sendMessage("-------- Page 1/" + maxPages + " --------");
					});
				} else if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("rl")) {
					ServerBackup.getInstance().reloadConfig();

					ServerBackup.getInstance().stopTimer();
					ServerBackup.getInstance().startTimer();

					String oldDes = ServerBackup.getInstance().backupDestination;

					if (!oldDes
							.equalsIgnoreCase(ServerBackup.getInstance().getConfig().getString("BackupDestination"))) {
						ServerBackup.getInstance().backupDestination = ServerBackup.getInstance().getConfig()
								.getString("BackupDestination");

						ServerBackup.getInstance().getLogger().log(Level.INFO,
								"ServerBackup: Backup destination [" + oldDes + " >> "
										+ ServerBackup.getInstance().backupDestination + "] updated successfully.");
					}

					if(ServerBackup.getInstance().cloudInfo.exists()){
						ServerBackup.getInstance().saveCloud();
					}

					ServerBackup.getInstance().loadFiles();

					sender.sendMessage(backup.processMessage("Command.Reload"));
				} else if (args[0].equalsIgnoreCase("tasks") || args[0].equalsIgnoreCase("task")) {
					if (BackupManager.tasks.size() > 0) {
						sender.sendMessage(backup.processMessage("Command.Tasks.Header"));

						for (String task : BackupManager.tasks) {
							sender.sendMessage(task);
						}

						sender.sendMessage(backup.processMessage("Command.Tasks.Footer"));
					} else {
						sender.sendMessage(backup.processMessage("Error.NoTasks"));
					}
				} else {
					sendHelp(sender);
				}
			}

			if (args.length == 2) {
				if (args[0].equalsIgnoreCase("list")) {
					Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
						File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

						if (backups.length == 0
								|| backups.length == 1 && backups[0].getName().equalsIgnoreCase("Files")) {
							sender.sendMessage(backup.processMessage("Error.NoBackups"));

							return;
						}

						Arrays.sort(backups);

						try {
							int page = Integer.valueOf(args[1]);

							if ((backups.length - 1) < page * 10 - 9) {
								sender.sendMessage("Try a lower value.");

								return;
							}

							if ((backups.length - 1) <= page * 10 && (backups.length - 1) >= page * 10 - 10) {
								sender.sendMessage("----- Backup " + Integer.valueOf(page * 10 - 9) + "-"
										+ (backups.length - 1) + "/" + (backups.length - 1) + " -----");
							} else {
								sender.sendMessage("----- Backup " + Integer.valueOf(page * 10 - 9) + "-"
										+ Integer.valueOf(page * 10) + "/" + (backups.length - 1) + " -----");
							}
							sender.sendMessage("");

							for (int i = page * 10 - 10; i < (backups.length - 1) && i < page * 10; i++) {
								if (backups[0].getName().equalsIgnoreCase("Files")) {
									i--;
									continue;
								}

								double fileSize = (double) FileUtils.sizeOf(backups[i]) / 1000 / 1000;
								fileSize = Math.round(fileSize * 100.0) / 100.0;

								if (sender instanceof Player) {
									Player p = (Player) sender;

									TextComponent msg = new TextComponent("§7[" + Integer.valueOf(i + 1) + "] §r"
											+ backups[i].getName() + " §7[" + fileSize + "MB]");
									msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
											new ComponentBuilder("Click to get Backup name").create()));
									msg.setClickEvent(
											new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, backups[i].getName()));

									p.spigot().sendMessage(msg);
								} else {
									sender.sendMessage(backups[i].getName());
								}
							}

							int maxPages = (backups.length - 1) / 10;

							if ((backups.length - 1) % 10 != 0) {
								maxPages++;
							}

							sender.sendMessage("");
							sender.sendMessage("-------- Page " + page + "/" + maxPages + " --------");
						} catch (Exception e) {
							sender.sendMessage(backup.processMessage("Error.NotANumber").replaceAll("%input%", args[1]));
						}
					});
				} else if (args[0].equalsIgnoreCase("ftp")) {
					if (args[1].equalsIgnoreCase("list")) {
						Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
							FtpManager ftpm = new FtpManager(sender);

							List<String> backups = ftpm.getFtpBackupList();

							if (backups.size() == 0) {
								sender.sendMessage(backup.processMessage("Error.NoFtpBackups"));

								return;
							}

							if (backups.size() < 10) {
								sender.sendMessage(
										"----- Ftp-Backup 1-" + backups.size() + "/" + backups.size() + " -----");
							} else {
								sender.sendMessage("----- Ftp-Backup 1-10/" + backups.size() + " -----");
							}
							sender.sendMessage("");

							for (int i = 0; i < backups.size() && i < 10; i++) {
								if (sender instanceof Player) {
									Player p = (Player) sender;
									TextComponent msg = new TextComponent(backups.get(i));
									msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
											new ComponentBuilder("Click to get Backup name").create()));
									msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
											backups.get(i).split(" ")[1]));

									p.spigot().sendMessage(msg);
								} else {
									sender.sendMessage(backups.get(i));
								}
							}

							int maxPages = backups.size() / 10;

							if (backups.size() % 10 != 0) {
								maxPages++;
							}

							sender.sendMessage("");
							sender.sendMessage("--------- Page 1/" + maxPages + " ---------");
						});
					}
				} else if (args[0].equalsIgnoreCase("zip")) {
					String filePath = args[1];

					if (args[1].contains(".zip")) {
						sender.sendMessage(backup.processMessage("Error.AlreadyZip").replaceAll("%file%", args[1]));
						return false;
					}

					File file = new File(ServerBackup.getInstance().backupDestination + "//" + filePath);
					File newFile = new File(ServerBackup.getInstance().backupDestination + "//" + filePath + ".zip");

					if (!newFile.exists()) {
						sender.sendMessage(backup.processMessage("Command.Zip.Header"));

						if (file.exists()) {
							try {
								ZipManager zm = new ZipManager(file.getPath(), newFile.getPath(), sender, true, false,
										true);

								zm.zip();
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {
							sender.sendMessage(backup.processMessage("Error.NoBackupFound").replaceAll("%file%", args[1]));
						}
					} else {
						sender.sendMessage(backup.processMessage("Error.FolderExists").replaceAll("%file%", args[1]));
					}
				} else if (args[0].equalsIgnoreCase("unzip")) {
					String filePath = args[1];

					if (!args[1].contains(".zip")) {
						sender.sendMessage(backup.processMessage("Error.NotAZip").replaceAll("%file%", args[1]));
						return false;
					}

					File file = new File(ServerBackup.getInstance().backupDestination + "//" + filePath);
					File newFile = new File(
							ServerBackup.getInstance().backupDestination + "//" + filePath.replaceAll(".zip", ""));

					if (!newFile.exists()) {
						sender.sendMessage(backup.processMessage("Command.Unzip.Header"));

						if (file.exists()) {
							ZipManager zm = new ZipManager(file.getPath(),
									ServerBackup.getInstance().backupDestination + "//" + newFile.getName(), sender,
									false, true, true);

							zm.unzip();
						} else {
							sender.sendMessage(backup.processMessage("Error.NoBackupFound").replaceAll("%file%", args[1]));
						}
					} else {
						sender.sendMessage(backup.processMessage("Error.ZipExists").replaceAll("%file%", args[1]));
					}
				} else if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("remove")) {
					if (args[1].equalsIgnoreCase("Files")) {
						sender.sendMessage("You can not delete the 'Files' backup folder.");

						return false;
					}

					BackupManager bm = new BackupManager(args[1], sender, true);

					bm.removeBackup();
				} else if (args[0].equalsIgnoreCase("search")) {
					Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
						File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

						if (backups.length == 0) {
							sender.sendMessage(backup.processMessage("Error.NoBackups"));

							return;
						}

						List<File> backupsMatch = new ArrayList<>();

						for (int i = 0; i < backups.length; i++) {
							if (backups[i].getName().contains(args[1])) {
								backupsMatch.add(backups[i]);
							}
						}

						if (backupsMatch.size() == 0) {
							sender.sendMessage(backup.processMessage("NoBackupSearch").replaceAll("%input%", args[1]));

							return;
						}

						Collections.sort(backupsMatch);

						int count = 1;

						if (backupsMatch.size() < 10) {
							sender.sendMessage(
									"----- Backup 1-" + backupsMatch.size() + "/" + backupsMatch.size() + " -----");
						} else {
							sender.sendMessage("----- Backup 1-10/" + backupsMatch.size() + " -----");
						}
						sender.sendMessage("");

						for (File file : backupsMatch) {
							if (count <= 10 && count <= backupsMatch.size()) {
								double fileSize = (double) FileUtils.sizeOf(file) / 1000 / 1000;
								fileSize = Math.round(fileSize * 100.0) / 100.0;

								if (sender instanceof Player) {
									Player p = (Player) sender;

									TextComponent msg = new TextComponent("§7[" + Integer.valueOf(count) + "] §r"
											+ file.getName() + " §7[" + fileSize + "MB]");
									msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
											new ComponentBuilder("Click to get Backup name").create()));
									msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
											"/backup remove " + file.getName()));

									p.spigot().sendMessage(msg);
								} else {
									sender.sendMessage(file.getName());
								}
							}
							count++;
						}

						int maxPages = backupsMatch.size() / 10;

						if (backupsMatch.size() % 10 != 0) {
							maxPages++;
						}

						sender.sendMessage("");
						sender.sendMessage("-------- Page 1/" + maxPages + " --------");
					});
				}
			}

			if (args.length >= 2) {
				if (args.length == 3) {
					if (args[0].equalsIgnoreCase("search")) {
						Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
							File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

							if (backups.length == 0
									|| backups.length == 1 && backups[0].getName().equalsIgnoreCase("Files")) {
								sender.sendMessage(backup.processMessage("Error.NoBackups"));

								return;
							}

							List<File> backupsMatch = new ArrayList<>();

							for (int i = 0; i < backups.length; i++) {
								if (backups[i].getName().contains(args[1])) {
									backupsMatch.add(backups[i]);
								}
							}

							if (backupsMatch.size() == 0) {
								sender.sendMessage(backup.processMessage("NoBackupSearch").replaceAll("%input%", args[1]));

								return;
							}

							Collections.sort(backupsMatch);

							try {
								int page = Integer.valueOf(args[2]);

								if (backups.length < page * 10 - 9) {
									sender.sendMessage("Try a lower value.");

									return;
								}

								int count = page * 10 - 9;

								if (backupsMatch.size() <= page * 10 && backupsMatch.size() >= page * 10 - 10) {
									sender.sendMessage("----- Backup " + Integer.valueOf(page * 10 - 9) + "-"
											+ backupsMatch.size() + "/" + backupsMatch.size() + " -----");
								} else {
									sender.sendMessage("----- Backup " + Integer.valueOf(page * 10 - 9) + "-"
											+ Integer.valueOf(page * 10) + "/" + backupsMatch.size() + " -----");
								}
								sender.sendMessage("");

								for (File file : backupsMatch) {
									if (count <= page * 10 && count <= backupsMatch.size()) {
										double fileSize = (double) FileUtils.sizeOf(file) / 1000 / 1000;
										fileSize = Math.round(fileSize * 100.0) / 100.0;

										if (sender instanceof Player) {
											Player p = (Player) sender;

											TextComponent msg = new TextComponent("§7[" + Integer.valueOf(count)
													+ "] §r" + file.getName() + " §7[" + fileSize + "MB]");
											msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
													new ComponentBuilder("Click to get Backup name").create()));
											msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
													"/backup remove " + file.getName()));

											p.spigot().sendMessage(msg);
										} else {
											sender.sendMessage(file.getName());
										}
									}
									count++;
								}

								int maxPages = backupsMatch.size() / 10;

								if (backupsMatch.size() % 10 != 0) {
									maxPages++;
								}

								sender.sendMessage("");
								sender.sendMessage("-------- Page " + page + "/" + maxPages + " --------");
							} catch (Exception e) {
								sender.sendMessage(backup.processMessage("Error.NotANumber").replaceAll("%input%", args[2]));
							}
						});
					} else if (args[0].equalsIgnoreCase("ftp")) {
						if (args[1].equalsIgnoreCase("list")) {
							Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
								FtpManager ftpm = new FtpManager(sender);

								List<String> backups = ftpm.getFtpBackupList();

								if (backups.size() == 0) {
									sender.sendMessage(backup.processMessage("Error.NoFtpBackups"));

									return;
								}

								try {
									int page = Integer.valueOf(args[2]);

									if (backups.size() < page * 10 - 9) {
										sender.sendMessage("Try a lower value.");

										return;
									}

									if (backups.size() <= page * 10 && backups.size() >= page * 10 - 10) {
										sender.sendMessage("----- Ftp-Backup " + Integer.valueOf(page * 10 - 9) + "-"
												+ backups.size() + "/" + backups.size() + " -----");
									} else {
										sender.sendMessage("----- Ftp-Backup " + Integer.valueOf(page * 10 - 9) + "-"
												+ Integer.valueOf(page * 10) + "/" + backups.size() + " -----");
									}
									sender.sendMessage("");

									for (int i = page * 10 - 10; i < backups.size() && i < page * 10; i++) {
										if (sender instanceof Player) {
											Player p = (Player) sender;

											TextComponent msg = new TextComponent(backups.get(i));
											msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
													new ComponentBuilder("Click to get Backup name").create()));
											msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
													backups.get(i).split(" ")[1]));

											p.spigot().sendMessage(msg);
										} else {
											sender.sendMessage(backups.get(i));
										}
									}

									int maxPages = backups.size() / 10;

									if (backups.size() % 10 != 0) {
										maxPages++;
									}

									sender.sendMessage("");
									sender.sendMessage("--------- Page " + page + "/" + maxPages + " ---------");
								} catch (Exception e) {
									sender.sendMessage(backup.processMessage("Error.NotANumber").replaceAll("%input%", args[1]));
								}
							});
						} else if (args[1].equalsIgnoreCase("download")) {
							Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
								FtpManager ftpm = new FtpManager(sender);

								ftpm.downloadFileFromFtp(args[2]);
							});
						} else if (args[1].equalsIgnoreCase("upload")) {
							Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
								FtpManager ftpm = new FtpManager(sender);

								ftpm.uploadFileToFtp(args[2], false);
							});
						}
					} else if(args[0].equalsIgnoreCase("dropbox")) {
							if(args[1].equalsIgnoreCase("upload")) {
								Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), () -> {
									DropboxManager dm = new DropboxManager(sender);

									dm.uploadToDropbox(args[2]);
								});
							}
					}
				}

				if (args[0].equalsIgnoreCase("create")) {
					String fileName = args[1];

					boolean fullBackup = false;
					if (args.length > 2) {
						for (int i = 2; i < args.length; i++) {
							if (args[i].equalsIgnoreCase("-full")) {
								fullBackup = true;
							} else {
								fileName = fileName + " " + args[i];
							}
						}
					}

					File file = new File(fileName);

					if (!file.isDirectory() && !args[1].equalsIgnoreCase("@server")) {
						Bukkit.getScheduler().runTaskAsynchronously(ServerBackup.getInstance(), new Runnable() {

							@Override
							public void run() {
								try {
									File des = new File(ServerBackup.getInstance().backupDestination + "//Files//"
											+ file.getName().replaceAll("/", "-"));

									if (des.exists()) {
										des = new File(des.getPath()
												.replaceAll("." + FilenameUtils.getExtension(des.getName()), "") + " "
												+ String.valueOf(System.currentTimeMillis() / 1000) + "."
												+ FilenameUtils.getExtension(file.getName()));
									}

									Files.copy(file, des);

									sender.sendMessage(backup.processMessage("Info.BackupFinished").replaceAll("%file%", file.getName()));
								} catch (IOException e) {
									sender.sendMessage(backup.processMessage("Error.BackupFailed").replaceAll("%file%", file.getName()));
									e.printStackTrace();
								}
							}

						});
					} else {
						BackupManager bm = new BackupManager(fileName, sender, fullBackup);

						bm.createBackup();
					}
				}
			} else if (args.length == 0) {
				sendHelp(sender);
			}
		} else {
			sender.sendMessage(backup.processMessage("Error.NoPermission"));
		}

		return false;
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage("/backup reload - reloads the config");
		sender.sendMessage("");
		sender.sendMessage("/backup list <page> - shows a list of 10 backups");
		sender.sendMessage("");
		sender.sendMessage(
				"/backup search <search argument> <page> - shows a list of 10 backups that contain the given search argument");
		sender.sendMessage("");
		sender.sendMessage("/backup create <world> - creates a new backup of a world");
		sender.sendMessage("");
		sender.sendMessage("/backup remove <folder> - removes an existing backup");
		sender.sendMessage("");
		sender.sendMessage("/backup zip <folder> - zipping folder");
		sender.sendMessage("");
		sender.sendMessage("/backup unzip <file> - unzipping file");
		sender.sendMessage("");
		sender.sendMessage("/backup ftp <download/upload/list> - download, upload or list ftp backup files");
		sender.sendMessage("");
		sender.sendMessage("/backup dropbox upload <file> - upload a backup to dropbox");
		sender.sendMessage("");
		sender.sendMessage("/backup shutdown - shut downs the server after backup tasks are finished");
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		List<String> completions = new ArrayList<>();
		List<String> commands = new ArrayList<>();

		if (sender.hasPermission("backup.admin")) {
			if (args.length == 1) {
				commands.add("reload");
				commands.add("list");
				commands.add("search");
				commands.add("create");
				commands.add("remove");
				commands.add("zip");
				commands.add("unzip");
				commands.add("ftp");
				commands.add("dropbox");
				commands.add("tasks");
				commands.add("shutdown");

				StringUtil.copyPartialMatches(args[0], commands, completions);
			} else if (args.length == 2) {
				if (args[0].equalsIgnoreCase("list")) {
					File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

					int maxPages = backups.length / 10;

					if (backups.length % 10 != 0) {
						maxPages++;
					}

					for (int i = 1; i < maxPages + 1; i++) {
						commands.add(String.valueOf(i));
					}
				} else if (args[0].equalsIgnoreCase("remove")) {
					File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

					for (int i = 0; i < backups.length; i++) {
						commands.add(backups[i].getName());
					}
				} else if (args[0].equalsIgnoreCase("create")) {
					for (World world : Bukkit.getWorlds()) {
						commands.add((!Bukkit.getWorldContainer().getPath().equalsIgnoreCase("."))
								? Bukkit.getWorldContainer() + "/" + world.getName()
								: world.getName());
					}

					commands.add("@server");
				} else if (args[0].equalsIgnoreCase("zip")) {
					File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

					for (File backup : backups) {
						if (!backup.getName().endsWith(".zip")) {
							commands.add(backup.getName());
						}
					}
				} else if (args[0].equalsIgnoreCase("unzip")) {
					File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

					for (File backup : backups) {
						if (backup.getName().endsWith(".zip")) {
							commands.add(backup.getName());
						}
					}
				} else if (args[0].equalsIgnoreCase("ftp")) {
					commands.add("list");
					commands.add("download");
					commands.add("upload");
				} else if (args[0].equalsIgnoreCase("dropbox")) {
					commands.add("upload");
				}

				StringUtil.copyPartialMatches(args[1], commands, completions);
			} else if (args.length == 3) {
				if (args[0].equalsIgnoreCase("ftp")) {
					if (args[1].equalsIgnoreCase("list")) {
						FtpManager ftpm = new FtpManager(sender);

						List<String> backups = ftpm.getFtpBackupList();

						int maxPages = backups.size() / 10;

						if (backups.size() % 10 != 0) {
							maxPages++;
						}

						for (int i = 1; i < maxPages + 1; i++) {
							commands.add(String.valueOf(i));
						}
					} else if (args[1].equalsIgnoreCase("download")) {
						FtpManager ftpm = new FtpManager(sender);

						List<String> backups = ftpm.getFtpBackupList();

						for (String backup : backups) {
							commands.add(backup.split(" ")[1]);
						}
					} else if (args[1].equalsIgnoreCase("upload")) {
						File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

						for (File backup : backups) {
							if (backup.getName().endsWith(".zip")) {
								commands.add(backup.getName());
							}
						}
					}

				} else if(args[0].equalsIgnoreCase("dropbox")) {
						File[] backups = new File(ServerBackup.getInstance().backupDestination + "").listFiles();

						for (File backup : backups) {
							if (backup.getName().endsWith(".zip")) {
								commands.add(backup.getName());
							}
						}
				} else if (args[0].equalsIgnoreCase("create")) {
					commands.add("-full");
				}

				StringUtil.copyPartialMatches(args[2], commands, completions);
			} else if (args.length > 3) {
				if (args[0].equalsIgnoreCase("create")) {
					commands.add("-full");
				}

				StringUtil.copyPartialMatches(args[args.length - 1], commands, completions);
			}
		}

		Collections.sort(completions);

		return completions;
	}

}
