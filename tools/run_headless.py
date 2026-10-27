import os, glob, subprocess, shutil

JDK21 = r"C:\Users\Dewey\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin"
JAVAC = os.path.join(JDK21, "javac.exe")
JAR = os.path.join(JDK21, "jar.exe")

MOD = r"E:\Minecraft Mods\Holey Digsite"
PACK = r"E:\Minecraft Mods\Holey Digsite Pack"
HOLD = r"C:\Users\Dewey\curseforge\minecraft\Instances\Test1\mods"

WORK = os.path.join(MOD, "build", "holeytest")
shutil.rmtree(WORK, ignore_errors=True)
os.makedirs(os.path.join(WORK, "classes", "META-INF"), exist_ok=True)

print("Compiling mod classes...")
subprocess.check_call([os.path.join(MOD, "gradlew.bat"), "compileJava", "-q"], cwd=MOD)

print("Gathering classpath for HoleyTest...")
merged_jar = os.path.join(MOD, "build", "moddev", "artifacts", "neoforge-21.1.252-merged.jar")
mod_classes = os.path.join(MOD, "build", "classes", "java", "main")
cp_jars = [merged_jar, mod_classes]

cache_dir = r"C:\Users\Dewey\.gradle\caches\modules-2\files-2.1"
for root, dirs, files in os.walk(cache_dir):
    for f in files:
        if f.endswith(".jar") and "sources" not in f:
            cp_jars.append(os.path.join(root, f))

cp_str = os.pathsep.join(cp_jars)

holey_test_java = os.path.join(PACK, "_source", "headless-test", "HoleyTest.java")
classes_dir = os.path.join(WORK, "classes")

print("Compiling HoleyTest.java with JDK 21...")
subprocess.check_call([JAVAC, "-proc:none", "-nowarn", "-implicit:none", "-d", classes_dir, "-cp", cp_str, holey_test_java])

shutil.copy(os.path.join(PACK, "_source", "headless-test", "neoforge.mods.toml"), os.path.join(classes_dir, "META-INF"))

holey_test_jar = os.path.join(WORK, "holeytest-1.0.0.jar")
print("Packaging holeytest-1.0.0.jar...")
subprocess.check_call([JAR, "cf", holey_test_jar, "-C", classes_dir, "."])

# Setup run
run_mods = os.path.join(MOD, "run", "mods")
os.makedirs(run_mods, exist_ok=True)
for f in os.listdir(run_mods):
    if f.endswith(".jar"):
        os.remove(os.path.join(run_mods, f))

for pattern in ["SkyblockBuilder-*.jar", "LibX-*.jar", "holeydigsite_pack-*.jar"]:
    for match in glob.glob(os.path.join(HOLD, pattern)):
        shutil.copy(match, run_mods)

shutil.copy(holey_test_jar, run_mods)

sb_dest = os.path.join(MOD, "run", "config", "skyblockbuilder")
shutil.rmtree(sb_dest, ignore_errors=True)
shutil.copytree(os.path.join(PACK, "config", "skyblockbuilder"), sb_dest)

with open(os.path.join(MOD, "run", "eula.txt"), "w") as f:
    f.write("eula=true\n")

world_dir = os.path.join(MOD, "run", "world")
shutil.rmtree(world_dir, ignore_errors=True)

print("Starting Headless Pass 1...")
pass1_log = os.path.join(MOD, "run", "headless_pass1.log")
with open(pass1_log, "w") as out:
    p1 = subprocess.run([os.path.join(MOD, "gradlew.bat"), "runServer", "--no-daemon"], cwd=MOD, stdout=out, stderr=subprocess.STDOUT, timeout=600)

print("Starting Headless Pass 2 (reload pass)...")
pass2_log = os.path.join(MOD, "run", "headless_pass2.log")
env = os.environ.copy()
env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " -Dholeytest.reload=true").strip()
with open(pass2_log, "w") as out:
    p2 = subprocess.run([os.path.join(MOD, "gradlew.bat"), "runServer", "--no-daemon"], cwd=MOD, stdout=out, stderr=subprocess.STDOUT, env=env, timeout=600)

# Clean run/mods
for f in os.listdir(run_mods):
    if f.endswith(".jar"):
        os.remove(os.path.join(run_mods, f))

print("=== HOLEYTEST RESULTS ===")
for path in [pass1_log, pass2_log]:
    if os.path.isfile(path):
        with open(path) as f:
            for line in f:
                if "HOLEYTEST" in line and ("FAIL" in line or "DONE" in line):
                    print(line.strip())
