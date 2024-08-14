package com.konloch.gitwatch;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * @author Konloch
 * @since 8/14/2024
 */
public class GitWatch
{
	private static boolean DELAYED_COMMIT = false;
	private static final long INACTIVITY_PERIOD = 10 * 60 * 1000; // 10 minutes
	private static final long DELAY_MS = 2000;
	private static final String COMMIT_MSG = "\"Cleanup\"";
	
	private final ScheduledExecutorService scheduler;
	private final WatchService watchService;
	private final Map<WatchKey, Path> keyDirectoryMap;
	private final HashSet<String> delayedCommitSet = new HashSet<>();
	
	public GitWatch(Path startDir) throws IOException
	{
		this.watchService = FileSystems.getDefault().newWatchService();
		this.keyDirectoryMap = new HashMap<>();
		this.scheduler = Executors.newScheduledThreadPool(1);
		
		registerAll(startDir);
		startDelayCommit();
	}
	
	private void startDelayCommit()
	{
		scheduler.scheduleAtFixedRate(() ->
		{
			delayedCommitSet.removeIf((path) ->
			{
				Path fullPath = Paths.get(path);
				try
				{
					long lastModified = Files.getLastModifiedTime(fullPath).toMillis();
					if (System.currentTimeMillis() - lastModified >= INACTIVITY_PERIOD)
					{
						System.out.println("File modified: " + fullPath);
						runGitCommand(fullPath, "git", "add", fullPath.toString());
						runGitCommand(fullPath, "git", "commit", "-m", COMMIT_MSG);
						return true;
					}
				}
				catch (IOException | InterruptedException e)
				{
					e.printStackTrace();
				}
				
				return false;
			});
		}, 0, 1, TimeUnit.MINUTES);
	}
	
	private void registerAll(final Path start) throws IOException
	{
		Files.walkFileTree(start, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
			{
				registerDirectory(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	private void registerDirectory(Path dir) throws IOException
	{
		WatchKey key = dir.register(watchService, ENTRY_MODIFY);
		keyDirectoryMap.put(key, dir);
	}
	
	public void processEvents()
	{
		while (true)
		{
			WatchKey key;
			
			try
			{
				key = watchService.poll(1, TimeUnit.MINUTES);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
				return;
			}
			
			if (key == null)
				continue;
			
			Path dir = keyDirectoryMap.get(key);
			
			if (dir == null)
				continue;
			
			for (WatchEvent<?> event : key.pollEvents())
			{
				WatchEvent.Kind<?> kind = event.kind();
				
				if (kind == OVERFLOW)
					continue;
				
				WatchEvent<Path> ev = (WatchEvent<Path>) event;
				Path filename = ev.context();
				Path fullPath = dir.resolve(filename);
				
				//skip files ending with ~
				if (filename.toString().endsWith("~"))
					continue;
				
				//recheck if file is still modified
				if (Files.exists(fullPath) && !Files.isDirectory(fullPath))
				{
					try
					{
						long fileSize = Files.size(fullPath);
						
						//skip empty files
						if (fileSize <= 0)
							continue;
						
						if (DELAYED_COMMIT)
							delayedCommitSet.add(fullPath.toString());
						else
						{
							//delay to allow file syncing
							try
							{
								TimeUnit.MILLISECONDS.sleep(DELAY_MS);
							}
							catch (InterruptedException e)
							{
								e.printStackTrace();
							}
							
							System.out.println("File modified: " + fullPath);
							runGitCommand(fullPath, "git", "add", fullPath.toString());
							runGitCommand(fullPath, "git", "commit", "-m", COMMIT_MSG);
						}
					}
					catch (IOException | InterruptedException e)
					{
						e.printStackTrace();
					}
				}
			}
			
			boolean valid = key.reset();
			if (!valid)
			{
				keyDirectoryMap.remove(key);
				
				if (keyDirectoryMap.isEmpty())
					break;
			}
		}
	}
	
	private static void runGitCommand(Path fullPath, String... command) throws IOException, InterruptedException
	{
		Path parent = fullPath.getParent();
		Process process = new ProcessBuilder()
				.directory(parent.toFile())
				.command(command)
				.start();
		process.waitFor();
	}
	
	public static void main(String[] args)
	{
		if (args.length == 0)
		{
			System.out.println("Usage: GitWatch <directory>");
			return;
		}
		
		Path dir = Paths.get(args[0]);
		if (!Files.isDirectory(dir))
		{
			System.out.println("The provided path is not a directory.");
			return;
		}
		
		//CLI arguments
		for(int i = 1; i < args.length; i++)
		{
			switch(args[i])
			{
				case "-delayed":
					DELAYED_COMMIT = true;
					break;
			}
		}
		
		//start the GitWatch service
		try
		{
			GitWatch watcher = new GitWatch(dir);
			System.out.println("Watching directory: " + dir);
			watcher.processEvents();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
