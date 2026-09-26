"""Rebuild hand-authored 2.5D depth grids for the two test fixture wallpapers.

No model/download required. Depth is artistic, normalized near=1/far=0,
not a metric reconstruction. Original JPEGs are copied without modification.
"""
from pathlib import Path
import math
import shutil

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/src/androidTest/assets/spatial'
COLS, ROWS = 48, 96


def polygon_depth(x, y, points, feather=0.035):
    inside = False
    distance = 10.0
    for (ax, ay), (bx, by) in zip(points, points[1:] + points[:1]):
        if (ay > y) != (by > y) and x < (bx-ax)*(y-ay)/(by-ay)+ax:
            inside = not inside
        dx, dy = bx-ax, by-ay
        t = max(0, min(1, ((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)))
        distance = min(distance, math.hypot(x-ax-t*dx, y-ay-t*dy))
    t = max(0, min(1, 0.5 + (distance if inside else -distance)/feather))
    return t*t*(3-2*t)


PANDA = [(0.30,0.426),(0.41,0.39),(0.4,0.378),(0.43,0.376),
         (0.51,0.375),(0.59,0.39),(0.60,0.376),(0.62,0.385),
         (0.60,0.4),(0.73,0.426),(0.63,0.431),(0.65,0.493),
         (0.73,0.522),(0.79,0.589),(0.87,0.673),(0.94,0.688),
         (0.94,0.714),(0.83,0.731),(0.82,0.777),(0.70,0.803),
         (0.53,0.809),(0.29,0.802),(0.21,0.767),(0.20,0.729),
         (0.09,0.714),(0.10,0.684),(0.16,0.671),(0.21,0.589),
         (0.28,0.526),(0.37,0.492),(0.39,0.432)]
LEFT = [(0,0.072),(0.15,0.118),(0.25,0.217),(0.52,0.292),
        (0.26,0.318),(0.23,0.40),(0.29,0.48),(0.29,0.60),
        (0.24,0.72),(0.28,1),(0,1)]
RIGHT = [(1,0.095),(0.89,0.105),(0.76,0.189),(0.66,0.283),
         (0.83,0.32),(0.73,0.43),(0.58,0.46),(0.58,0.62),
         (0.52,0.70),(0.62,0.81),(0.48,1),(1,1)]


def depth(scene, x, y):
    if scene == 2:
        subject = polygon_depth(x, y, PANDA)
        volume = math.exp(-((x-0.51)/0.27)**2-((y-0.63)/0.25)**2)
        return 0.08 + subject*(0.58+0.29*volume)
    # Valley vanishing point and distant underwater channel remain recessed.
    water = max(0, min(1, (y-0.29)/0.70))
    base = 0.04 + water*0.35
    cliffs = max(polygon_depth(x,y,LEFT), polygon_depth(x,y,RIGHT))
    return min(1, base + cliffs*(0.32+0.28*y))


if __name__ == '__main__':
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for scene in (1, 2):
        shutil.copyfile(ROOT/f'wallpapers/wallpaper-{scene}.JPG', OUTPUT/f'wallpaper-{scene}.jpg')
        values = [depth(scene, x/COLS, y/ROWS) for y in range(ROWS+1) for x in range(COLS+1)]
        (OUTPUT/f'wallpaper-{scene}.depth').write_text(
            f'{COLS} {ROWS}\n' + '\n'.join(' '.join(f'{v:.5f}' for v in values[i:i+COLS+1])
                                        for i in range(0,len(values),COLS+1)) + '\n')
