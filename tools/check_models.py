"""Finds block and item models the game will refuse to load: element bounds outside -16..32 or an unsupported rotation."""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite/models')


def main():
    bad = 0
    files = glob.glob(os.path.join(MODELS, '**', '*.json'), recursive=True)
    for path in files:
        with open(path, encoding='utf-8') as fh:
            model = json.load(fh)
        for el in model.get('elements', []):
            rotation = el.get('rotation')
            if any(c < -16 or c > 32 for c in el['from'] + el['to']) or (rotation and rotation.get('angle') not in (0, 22.5, -22.5, 45, -45)):
                print(os.path.relpath(path, MODELS), el['from'], el['to'], rotation)
                bad += 1
    print(f'{len(files)} models checked, {bad} bad elements')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
