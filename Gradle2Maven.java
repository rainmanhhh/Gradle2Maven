import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

public class Gradle2Maven implements Runnable {
  static final String KeyGradleCachePath = "gradleCachePath";
  static final String KeyMavenLocalRepoPath = "mavenLocalRepoPath";
  static final String KeyExclude = "exclude";
  static final String KeyDryRun = "dryRun";
  final Path gradleCachePath;
  final Path mavenLocalRepoPath;
  final Pattern exclude;
  final boolean dryRun;

  Gradle2Maven(String gradleCachePath, String mavenLocalRepoPath, String exclude, boolean dryRun) {
    this.gradleCachePath = Paths.get(Objects.requireNonNull(gradleCachePath));
    this.mavenLocalRepoPath = Paths.get(Objects.requireNonNull(mavenLocalRepoPath));
    this.exclude = Pattern.compile(exclude == null ? "" : exclude);
    this.dryRun = dryRun;
  }

  public static void main(String[] args) throws IOException {
    System.out.println("reading configFile: config.properties(if not exist, this app will create a default one)");
    String configFileName = "config.properties";
    File configFile = new File(configFileName);
    if (!configFile.isFile()) {
      if (!configFile.createNewFile()) throw new IOException("create configFile failed: " + configFileName);
      Files.write(configFile.toPath(), getDefaultConfig().getBytes(StandardCharsets.UTF_8));
    }
    Properties config = new Properties();
    try (FileInputStream inputStream = new FileInputStream(configFile)) {
      config.load(inputStream);
    }
    String gradleCachePath = config.getProperty(KeyGradleCachePath);
    String mavenLocalRepoPath = config.getProperty(KeyMavenLocalRepoPath);
    String exclude = config.getProperty(KeyExclude);
    boolean dryRun = Boolean.parseBoolean(config.getProperty(KeyDryRun, String.valueOf(false)));
    new Gradle2Maven(gradleCachePath, mavenLocalRepoPath, exclude, dryRun).run();
  }

  @Override
  public void run() {
    System.out.println("moving caches from " + KeyGradleCachePath + ": " + gradleCachePath + " to " + KeyMavenLocalRepoPath + ": " + mavenLocalRepoPath + ", exclude: " + exclude);
    //gradle cache item path format：cacheRoot/group/artifact/version/hash/item
    for (Path groupDir : subDirs(gradleCachePath)) {
      for (Path artifactDir : subDirs(groupDir)) {
        for (Path versionDir: subDirs(artifactDir)) {
          String GAV = groupDir.getFileName() + "/" + artifactDir.getFileName() + "/" + versionDir.getFileName();
          if (exclude.matcher(GAV).matches()) {
            System.out.println("skip excluded dir: " + GAV);
            continue; //skip excluded dirs
          }
          for (Path hashDir: subDirs(versionDir)) {
            moveHashDirToMaven(groupDir, versionDir, hashDir);
          }
          if (!dryRun) deleteEmptyDir(versionDir);
        }
        if (!dryRun) deleteEmptyDir(artifactDir);
      }
      if (!dryRun) deleteEmptyDir(groupDir);
    }
    if (!dryRun) System.out.println("move caches success");
  }

  void moveHashDirToMaven(Path groupDir, Path versionDir, Path hashDir) {
    Path groupToVersionPath = groupDir.relativize(versionDir);
    for (File file : files(hashDir.toFile())) {
      String[] groupParts = groupDir.toFile().getName().split("\\.");
      Path mavenRootToGroupPath = groupParts.length > 1 ?
        Paths.get(groupParts[0], Arrays.copyOfRange(groupParts, 1, groupParts.length)):
        groupDir.getFileName();
      Path targetDir = mavenLocalRepoPath.resolve(mavenRootToGroupPath).resolve(groupToVersionPath);
      System.out.println("move file: " + file.getAbsolutePath() + " to dir: " + targetDir);
      if (!dryRun) {
        try {
          if (!targetDir.toFile().exists() && !targetDir.toFile().mkdirs())
            throw new RuntimeException("create target dir failed: " + targetDir);
          Files.move(file.toPath(), targetDir.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    if (!dryRun) {
      try {
        Files.delete(hashDir);
      } catch (IOException e) {
        throw new RuntimeException("delete hashDir failed: " + hashDir.toAbsolutePath(), e);
      }
    }
  }

  static void deleteEmptyDir(Path dirPath) {
    File dir = dirPath.toFile();
    String[] children = dir.list();
    if (children != null && children.length == 0) {
      if (!dir.delete()) {
        throw new RuntimeException("delete empty dir failed: " + dirPath.toAbsolutePath());
      }
    }
  }

  static Path[] subDirs(Path path) {
    File dir = path.toFile();
    File[] subDirs = dir.listFiles(File::isDirectory);
    if (subDirs == null) throw new NullPointerException("cannot read subDirs of dir: " + dir.getAbsolutePath());
    Path[] paths = new Path[subDirs.length];
    for (int i = 0; i < subDirs.length; i++) {
      paths[i] = subDirs[i].toPath();
    }
    return paths;
  }

  static File[] files(File dir) {
    File[] files = dir.listFiles();
    if (files == null) throw new NullPointerException("cannot read files of dir: " + dir.getAbsolutePath());
    return files;
  }

  static String getDefaultConfig() {
    return KeyGradleCachePath + "=" + getDefaultGradleCachePath() + "\n"
      + KeyMavenLocalRepoPath + "=" + getDefaultMavenLocalRepoPath() + "\n"
      + KeyExclude + "=" + getDefaultExclude() + "\n"
      + KeyDryRun + "=true";
  }

  static String normalizePath(String origin) {
    String result = origin.replace('\\', '/');
    if (result.endsWith("/")) result = result.substring(0, result.length() - 1);
    return result;
  }

  static String getUserHome() {
    String userHome = System.getProperty("user.home");
    return normalizePath(userHome);
  }

  static String getGradleUserHome() {
    String gradleUserHome = System.getenv("GRADLE_USER_HOME");
    if (gradleUserHome == null) gradleUserHome = getUserHome() + "/.gradle";
    return normalizePath(gradleUserHome);
  }

  static String getDefaultGradleCachePath() {
    return getGradleUserHome() + "/caches/modules-2/files-2.1";
  }

  static String getM2Home() {
    String m2Home = System.getenv("M2_HOME");
    if (m2Home == null) m2Home = getUserHome() + "/.m2";
    return normalizePath(m2Home);
  }

  static String getDefaultMavenLocalRepoPath() {
    return getM2Home() + "/repository";
  }

  static String getDefaultExclude() {
    return "gradle/gradle/.+";
  }
}
