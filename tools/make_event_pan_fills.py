"""Pan fills for the event dirts: the moss fill models with each event rock as the dirt, plus their blockstate variants."""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite')
KINDS = {9: 'red_dirt', 10: 'mullock', 11: 'opal_clay'}
PANS = {'pan': 4, 'large_pan': 8}


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(data, fh, indent=2)
        fh.write('\n')


def main():
    n = 0
    for pan, fills in PANS.items():
        state_path = os.path.join(ASSETS, 'blockstates', f'{pan}.json')
        with open(state_path, encoding='utf-8') as fh:
            states = json.load(fh)
        for tier, kind in KINDS.items():
            states['variants'][f'fill=0,tier={tier}'] = {'model': f'holeydigsite:block/{pan}'}
            for fill in range(1, fills + 1):
                with open(os.path.join(ASSETS, 'models/block', f'{pan}_fill_{fill}_moss.json'), encoding='utf-8') as fh:
                    model = json.load(fh)
                model['textures']['dirt'] = f'holeydigsite:block/{kind}_rock'
                write(os.path.join(ASSETS, 'models/block', f'{pan}_fill_{fill}_{kind}.json'), model)
                states['variants'][f'fill={fill},tier={tier}'] = {'model': f'holeydigsite:block/{pan}_fill_{fill}_{kind}'}
                n += 1
        write(state_path, states)
    print(f'{n} event pan fills')


if __name__ == '__main__':
    main()
