"""Generate the launcher's v1 full-canvas layers and regular depth grids."""
import cv2
import numpy as np
from PIL import Image


def normalize_depth(raw, size):
    raw = np.asarray(raw, np.float32)
    if raw.ndim != 2 or not np.isfinite(raw).all() or np.any(raw <= 0):
        raise ValueError('Depth model returned invalid positive depth')
    # DA3 predicts distance, while the Android renderer uses near=1.
    inverse = 1 / np.maximum(raw, 1e-6)
    low, high = np.percentile(inverse, [2, 98])
    depth = np.full_like(inverse, .5) if high - low < 1e-6 else ((inverse-low)/(high-low)).clip(0, 1)
    return cv2.resize(depth, size, interpolation=cv2.INTER_LINEAR)


def mesh_text(depth):
    grid = cv2.resize(depth.astype(np.float32), (49, 97), interpolation=cv2.INTER_LINEAR)
    # Bound neighboring displacement differences, preventing folds at steep edges.
    for _ in range(2):
        for y in range(97):
            for x in range(49):
                if x: grid[y, x] = np.clip(grid[y, x], grid[y, x-1]-.18, grid[y, x-1]+.18)
                if y: grid[y, x] = np.clip(grid[y, x], grid[y-1, x]-.18, grid[y-1, x]+.18)
        for y in range(96, -1, -1):
            for x in range(48, -1, -1):
                if x < 48: grid[y, x] = np.clip(grid[y, x], grid[y, x+1]-.18, grid[y, x+1]+.18)
                if y < 96: grid[y, x] = np.clip(grid[y, x], grid[y+1, x]-.18, grid[y+1, x]+.18)
    return '48 96\n' + '\n'.join(' '.join(f'{v:.6f}' for v in row) for row in grid.clip(0, 1)) + '\n'


def generate(image, models, max_layers):
    depth = normalize_depth(models.depth(image), image.size)
    alpha = np.asarray(models.mask(image), np.float32)
    if alpha.ndim != 2 or not np.isfinite(alpha).all():
        raise ValueError('Segmentation model returned invalid mask')
    alpha = cv2.resize(alpha, image.size).clip(0, 1)
    alpha[alpha <= .02] = 0
    flags = []
    support = (alpha > .02).astype(np.uint8)
    coverage = float(support.mean())
    if coverage < .005 or coverage > .85:
        return [(image.convert('RGBA'), depth)], ['single_layer_unreliable_segmentation']
    if coverage > .45:
        flags.append('large_inpaint_region_review_recommended')
    radius = max(3, round(max(image.size) * .015))
    hole = cv2.dilate(support, cv2.getStructuringElement(cv2.MORPH_ELLIPSE,
                                                    (radius*2+1, radius*2+1)))
    filled = np.asarray(models.inpaint(image, hole))
    original = np.asarray(image)
    background = Image.fromarray(np.where(hole[..., None] != 0, filled, original).astype(np.uint8)).convert('RGBA')
    # Fill depth under removed subjects too, so their silhouette cannot deform the backdrop.
    background_depth = cv2.inpaint(depth.astype(np.float32), hole, 5, cv2.INPAINT_NS).clip(0, 1) * .35
    count, labels, stats, _ = cv2.connectedComponentsWithStats(support, 8)
    components = [i for i in range(1, count) if stats[i, cv2.CC_STAT_AREA] >= support.size*.005]
    components.sort(key=lambda i: float(np.mean(depth[labels == i])))
    # Preserve connected subjects; split only distinct, substantial silhouettes.
    groups = [list(g) for g in np.array_split(components, min(max_layers-1, len(components))) if len(g)] if components else [[]]
    assignments = np.zeros_like(labels)
    for index, group in enumerate(groups):
        assignments[np.isin(labels, group)] = index
    layers = [(background, background_depth)]
    for index, _ in enumerate(groups):
        layer_alpha = alpha * (assignments == index)
        rgba = np.dstack((original, (layer_alpha*255).round().astype(np.uint8)))
        # Transparent texels carry a stable depth too; depth bounds preserve layer order.
        low = .45 + index * (.5 / len(groups))
        layer_depth = low + depth * (.45 / len(groups))
        layers.append((Image.fromarray(rgba), layer_depth))
    return layers, flags
