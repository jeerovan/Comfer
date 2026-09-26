"""Lazy, pinned server-side model adapters. No inference runs on the phone."""
import hashlib
import numpy as np
from PIL import Image

DEPTH_ID = 'depth-anything/DA3MONO-LARGE'
DEPTH_REV = 'f465978e618db8cc79c83b8bbf24964857db1875'
MASK_ID = 'ZhengPeng7/BiRefNet_HR'
MASK_REV = 'a7a562f6fd16021180f2f4348f4de003a2d3d1e1'
LAMA_URL = 'https://github.com/enesmsahin/simple-lama-inpainting/releases/download/v0.1.0/big-lama.pt'
PROVENANCE = {'depth': [DEPTH_ID, DEPTH_REV], 'mask': [MASK_ID, MASK_REV],
              'inpainting': LAMA_URL, 'depth_code': '3d835ec1a5802d64a8b8b15f817a1ab54809bfe4'}

class Models:
    def __init__(self, device, cache, download):
        import torch
        from huggingface_hub import snapshot_download
        from depth_anything_3.api import DepthAnything3
        from transformers import AutoModelForImageSegmentation
        self.torch = torch
        self.device = torch.device(('cuda' if torch.cuda.is_available() else 'cpu')
                                   if device == 'auto' else device)
        self.depth_model = DepthAnything3.from_pretrained(snapshot_download(
            DEPTH_ID, revision=DEPTH_REV, cache_dir=str(cache / 'huggingface'))).eval()
        # Reviewed upstream custom model code, pinned to an immutable revision.
        self.mask_model = AutoModelForImageSegmentation.from_pretrained(
            MASK_ID, revision=MASK_REV, trust_remote_code=True,
            cache_dir=str(cache / 'huggingface')).eval()
        path = cache / 'big-lama.pt'
        if not path.exists():
            download(LAMA_URL, path, limit=250 * 1024 * 1024)
        self.lama = torch.jit.load(str(path), map_location='cpu').eval()
        self.provenance = dict(PROVENANCE, lama_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                               torch=torch.__version__, device=str(self.device))

    def _offload(self, model):
        model.to('cpu')
        if self.device.type == 'cuda':
            self.torch.cuda.empty_cache()

    def depth(self, image):
        model = self.depth_model.to(self.device)
        model.device = self.device  # DA3 caches this separately from module parameters.
        try:
            with self.torch.inference_mode():
                prediction = model.inference([image], process_res=1008,
                                             process_res_method='upper_bound_resize')
            return np.asarray(prediction.depth[0], dtype=np.float32)
        finally:
            self._offload(model)

    def mask(self, image):
        from torchvision import transforms
        transform = transforms.Compose([transforms.Resize((2048, 2048)),
            transforms.ToTensor(), transforms.Normalize([.485, .456, .406], [.229, .224, .225])])
        model = self.mask_model.to(self.device)
        try:
            with self.torch.inference_mode():
                output = model(transform(image).unsqueeze(0).to(self.device))[-1].sigmoid()
            return output[0, 0].float().cpu().numpy()
        finally:
            self._offload(model)

    def inpaint(self, image, mask):
        # Full output resolution; reflection padding is removed before saving.
        torch = self.torch
        rgb = np.asarray(image, dtype=np.float32) / 255
        height, width = rgb.shape[:2]
        pads = ((0, (-height) % 8), (0, (-width) % 8))
        rgb = np.pad(rgb, pads + ((0, 0),), mode='reflect')
        mask = np.pad(mask.astype(np.float32), pads, mode='reflect')
        model = self.lama.to(self.device)
        try:
            with torch.inference_mode():
                result = model(torch.from_numpy(rgb.transpose(2, 0, 1)).unsqueeze(0).to(self.device),
                               torch.from_numpy(mask).unsqueeze(0).unsqueeze(0).to(self.device))
            result = result[0].permute(1, 2, 0).float().cpu().numpy()[:height, :width]
            return Image.fromarray((result.clip(0, 1) * 255).round().astype(np.uint8))
        finally:
            self._offload(model)
