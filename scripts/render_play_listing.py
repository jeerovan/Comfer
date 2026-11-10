#!/usr/bin/env python3
"""Render the versioned Comfer Play artwork from real captures and editable copy.

Requires Pillow. Run with --directory store-listing/v002. Emits opaque upload
PNGs, corresponding editable SVGs, a hashed manifest and contact sheets.
"""
import argparse
import base64
from datetime import date
import hashlib
from html import escape
import io
import json
from pathlib import Path
import shutil

from PIL import Image, ImageDraw, ImageFont


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Artwork:
    def __init__(self, size, background, font_path):
        self.size, self.font_path = size, font_path
        self.image = Image.new('RGB', size, background)
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{size[0]}" height="{size[1]}" viewBox="0 0 {size[0]} {size[1]}">',
                    '<style>@font-face{font-family:Inter;src:url(../fonts/InterVariable.ttf)}text{font-family:Inter,Arial,sans-serif}</style>',
                    f'<rect width="100%" height="100%" fill="{background}"/>']
        self.index = 0

    def text(self, value, x, baseline, size, color, weight=550):
        font = ImageFont.truetype(str(self.font_path), size)
        axes = font.get_variation_axes()
        values = [float(a['default']) for a in axes]
        for index, axis in enumerate(axes):
            if b'Weight' in axis['name']:
                values[index] = weight
        font.set_variation_by_axes(values)
        box = self.draw.textbbox((x, baseline), value, font=font, anchor='ls')
        if box[0] < 0 or box[2] > self.size[0]-36:
            raise ValueError('Caption would clip: '+value)
        self.draw.text((x, baseline), value, font=font, fill=color, anchor='ls')
        self.svg.append(f'<text x="{x}" y="{baseline}" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(value)}</text>')
        return box

    def rect(self, box, color, radius=0):
        self.draw.rounded_rectangle(box, radius=radius, fill=color)
        x,y,right,bottom = box
        self.svg.append(f'<rect x="{x}" y="{y}" width="{right-x}" height="{bottom-y}" rx="{radius}" fill="{color}"/>')

    def capture(self, path, box, crop=None):
        source = Image.open(path).convert('RGB')
        sx,sy,width,height = crop or (0,0,*source.size)
        if min(sx,sy)<0 or sx+width>source.width or sy+height>source.height:
            raise ValueError('Crop exceeds source capture')
        left,top,right,bottom = box
        scale = min((right-left)/width, (bottom-top)/height)
        target_width, target_height = round(width*scale), round(height*scale)
        x,y = round((left+right-target_width)/2),round((top+bottom-target_height)/2)
        detail = source.crop((sx,sy,sx+width,sy+height)).resize((target_width,target_height), Image.Resampling.LANCZOS)
        self.image.paste(detail, (x,y))
        self.index += 1
        clip = f'capture-{self.index}'
        buffer = io.BytesIO();source.save(buffer,format='PNG')
        encoded = base64.b64encode(buffer.getvalue()).decode()
        self.svg.append(f'<defs><clipPath id="{clip}"><rect x="{x}" y="{y}" width="{target_width}" height="{target_height}"/></clipPath></defs>')
        self.svg.append(f'<g clip-path="url(#{clip})"><image x="{x-sx*scale}" y="{y-sy*scale}" width="{source.width*scale}" height="{source.height*scale}" xlink:href="data:image/png;base64,{encoded}"/></g>')

    def save(self, png, svg):
        png.parent.mkdir(parents=True,exist_ok=True)
        svg.parent.mkdir(parents=True,exist_ok=True)
        self.image.save(png, optimize=True)
        svg.write_text('\n'.join(self.svg)+ '\n</svg>\n')


def render(directory):
    directory = Path(directory).resolve()
    design = json.loads((directory/'design.json').read_text())
    copy = json.loads((directory/'copy.json').read_text())
    provenance = json.loads((directory/'captures/provenance.json').read_text())
    palette = design['palette']
    font = directory/'fonts/InterVariable.ttf'
    assets = []
    output = directory/'upload'
    editable = directory/'editable'
    for index, scene in enumerate(design['screenshots'], 1):
        capture = directory/'captures/phone'/scene['capture']
        image = Artwork((1080,1920),palette[scene['background']],font)
        image.text(scene['label'],72,109,25,palette['ink'],620)
        for line, text in enumerate(scene['headline']):
            image.text(text,68,220+line*106,91,palette['ink'],590)
        image.rect((64,392,1016,1858),'#FFFFFF',32)
        image.capture(capture,(70,410,1010,1838),scene.get('crop'))
        image.text(f'{index:02d} / 05',74,1896,19,palette['ink'],500)
        file = output/'phone'/f'{scene["name"]}.png'
        image.save(file,editable/f'{scene["name"]}.svg')
        assets.append({'kind':'phoneScreenshots','locale':'en-US','path':str(file.relative_to(directory)),
                       'sha256':digest(file),'capture_path':str(capture.relative_to(directory)),
                       'capture_sha256':digest(capture),'crop':scene.get('crop'),
                       'alt_text':scene['alt_text'],'caption_bounds':[68,85,950,248],
                       'fixture':provenance['fixture'],'source_artifact_sha256':provenance['apk_sha256']})
    feature = Artwork((1024,500),palette['sage'],font)
    feature.text('COMFER',180,158,19,palette['ink'],620)
    feature.text('Make yourself',176,233,49,palette['ink'],590)
    feature.text('at home.',176,294,49,palette['ink'],590)
    feature.rect((650,109,922,393),'#FFFFFF',30)
    home = directory/'captures/phone/01-home.png'
    feature.capture(home,(660,122,912,382),[210,1130,680,650])
    file = output/'feature-graphic.png'
    feature.save(file,editable/'feature-graphic.svg')
    assets.insert(0,{'kind':'featureGraphic','locale':'en-US','path':str(file.relative_to(directory)),
                     'sha256':digest(file),'capture_path':str(home.relative_to(directory)),
                     'capture_sha256':digest(home),'crop':[210,1130,680,650],
                     'alt_text':'Make yourself at home with Comfer, alongside its circular favorite-app layout',
                     'source_artifact_sha256':provenance['apk_sha256']})
    for device,kind in [('seven-inch','sevenInchScreenshots'),('ten-inch','tenInchScreenshots')]:
        for capture in sorted((directory/'captures'/device).glob('*.png')):
            file = output/device/capture.name
            file.parent.mkdir(parents=True,exist_ok=True)
            Image.open(capture).convert('RGB').save(file,optimize=True)
            scene = next(s for s in design['screenshots'] if s['capture']==capture.name)
            assets.append({'kind':kind,'locale':'en-US','path':str(file.relative_to(directory)),
                           'sha256':digest(file),'capture_path':str(capture.relative_to(directory)),
                           'capture_sha256':digest(capture),'alt_text':scene['alt_text'],
                           'fixture':provenance['fixture'],'source_artifact_sha256':provenance['apk_sha256']})
    (directory/'manifest.json').write_text(json.dumps({
        'version':design['version'],'label':design['label'],'hypothesis':design['hypothesis'],
        'specifications_checked':'2026-10-22','listing':copy,'recommendation_profile':True,
        'publication_state':'prepared_locally','activation_at':None,'assets':assets,
        'retained_assets':['icon'],'video_action':'retain_existing_url; no video generated',
        'publication_note':design['publication_note'],'regenerate':design['regenerate'],
    },ensure_ascii=False,indent=2)+'\n')
    text = directory/'upload/text';text.mkdir(parents=True,exist_ok=True)
    for field,name in [('title','title.txt'),('shortDescription','short-description.txt'),('fullDescription','full-description.txt')]:
        (text/name).write_text(copy[field]+'\n')
    for kind,folder in [('phoneScreenshots','phone'),('sevenInchScreenshots','seven-inch'),('tenInchScreenshots','ten-inch')]:
        files=[directory/a['path'] for a in assets if a['kind']==kind]
        width, height = (330,600) if folder!='ten-inch' else (490,310)
        sheet=Image.new('RGB',(width*len(files),height),'#E8E6E0');draw=ImageDraw.Draw(sheet)
        for index,file in enumerate(files):
            im=Image.open(file);im.thumbnail((width-20,height-44),Image.Resampling.LANCZOS)
            sheet.paste(im,(index*width+(width-im.width)//2,36))
            draw.text((index*width+12,10),file.stem,fill='#153B35')
        sheet.save(directory/f'contact-{folder}.jpg',quality=92)
    print(json.dumps({'assets':len(assets),'manifest':str(directory/'manifest.json')}))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True)
    render(parser.parse_args().directory)
