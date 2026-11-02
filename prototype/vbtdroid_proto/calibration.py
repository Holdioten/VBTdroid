"""相机视角与杠铃片尺寸标定。"""

from dataclasses import dataclass
from math import radians, sin


@dataclass(frozen=True)
class Calibration:
    plate_diameter_m: float
    px_per_m: float
    camera_angle_deg: float

    @classmethod
    def from_plate(cls, plate_diameter_m, major_axis_px, camera_angle_deg):
        if plate_diameter_m <= 0 or major_axis_px <= 0:
            raise ValueError("plate diameter and major axis must be positive")
        if not 0 < camera_angle_deg <= 90:
            raise ValueError("camera angle must be in (0, 90]")
        return cls(plate_diameter_m, major_axis_px / plate_diameter_m, camera_angle_deg)

    def to_meters(self, observation):
        return {
            "t_sec": observation["t_sec"],
            "x_m": (observation["x_px"] / self.px_per_m) / sin(radians(self.camera_angle_deg)),
            "y_m": -observation["y_px"] / self.px_per_m,
        }
