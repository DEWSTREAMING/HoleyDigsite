import os, glob, subprocess

JDK21 = r"C:\Users\Dewey\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin"
JAVAC = os.path.join(JDK21, "javac.exe")
JAVA = os.path.join(JDK21, "java.exe")

MOD = r"E:\Minecraft Mods\Holey Digsite"
PACK = r"E:\Minecraft Mods\Holey Digsite Pack"
HERE = os.path.join(MOD, "tools")
OUT_CLASSES = os.path.join(HERE, "world_snbt", "classes")
os.makedirs(OUT_CLASSES, exist_ok=True)

merged_jar = os.path.join(MOD, "build", "moddev", "artifacts", "neoforge-21.1.252-merged.jar")
nbt_tool = os.path.join(PACK, "_source", "island", "NbtTool.java")

cp_jars = [merged_jar]
cache_dir = r"C:\Users\Dewey\.gradle\caches\modules-2\files-2.1"
for root, dirs, files in os.walk(cache_dir):
    for f in files:
        if f.endswith(".jar") and "sources" not in f:
            cp_jars.append(os.path.join(root, f))

cp_str = os.pathsep.join(cp_jars)

print("Compiling NbtTool.java with JDK 21...")
subprocess.check_call([JAVAC, "-proc:none", "-d", OUT_CLASSES, "-cp", cp_str, nbt_tool])
print("Compiled NbtTool successfully.")

args = ["batch"]
snbt_dir = os.path.join(HERE, "world_snbt")
for root, dirs, files in os.walk(snbt_dir):
    for f in files:
        if f.endswith(".snbt"):
            rel_path = os.path.relpath(os.path.join(root, f), snbt_dir)
            if rel_path.startswith("store") or rel_path.startswith("plot"):
                snbt_file = os.path.join(root, f)
                nbt_file = os.path.join(MOD, "src", "main", "resources", "data", "holeydigsite", "structure", rel_path[:-5] + ".nbt")
                args.extend([snbt_file, nbt_file])

pack_snbt = os.path.join(PACK, "_source", "island", "holey_digsite.snbt")
pack_nbt = os.path.join(PACK, "config", "skyblockbuilder", "templates", "islands", "holey_digsite.nbt")
if os.path.isfile(pack_snbt):
    args.extend([pack_snbt, pack_nbt])

print(f"Converting {len(args)//2} structure files...")
subprocess.check_call([JAVA, "-cp", OUT_CLASSES + os.pathsep + cp_str, "NbtTool"] + args)
print("Finished converting NBT files!")
