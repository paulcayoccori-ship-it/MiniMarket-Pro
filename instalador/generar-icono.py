# Genera instalador/minimarket.ico (icono del programa). Requiere Pillow: pip install pillow
from pathlib import Path
from PIL import Image, ImageDraw

N = 512
im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
d.rounded_rectangle([16, 16, N - 16, N - 16], radius=96, fill=(17, 17, 17, 255))
w = (255, 255, 255, 255)
d.line([(110, 150), (160, 150), (205, 330), (390, 330)], fill=w, width=30, joint="curve")
d.polygon([(175, 190), (420, 190), (385, 290), (198, 290)], fill=w)
d.ellipse([200, 360, 250, 410], fill=w)
d.ellipse([340, 360, 390, 410], fill=w)

destino = Path(__file__).with_name("minimarket.ico")
im.save(destino, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("Icono generado:", destino)
