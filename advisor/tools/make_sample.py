"""Writes advisor/src/main/assets/vehicles.json, the bundled SAMPLE dataset.

None of these values were checked against Ryco, Penrite, Valvoline or the vehicle maker.
They exist so the app can be demonstrated, and every record is flagged verified=false.
A workshop replaces them by importing its own checked file (Settings > Service data).
"""
import json, pathlib

P = lambda id, title, steps, before=None, notes=None, tpms=None: {k: v for k, v in dict(
    id=id, title=title, before=before, steps=steps, notes=notes, tpmsType=tpms).items() if v is not None}

procedures = [
    P("toyota-mid-service", "Toyota – maintenance reminder via multi-info display",
      ["Use the steering-wheel arrow buttons to open the Settings (gear) screen on the multi-information display.",
       "Scroll to Vehicle Settings and press OK.",
       "Select Scheduled Maintenance.",
       "Choose Yes / Reset and press OK to confirm.",
       "Check the reminder has cleared, then switch the ignition OFF and ON again to confirm it stays off."],
      before="Vehicle stationary, ignition ON (engine may be off)."),
    P("toyota-odo-service", "Toyota – maintenance reminder via ODO/TRIP button",
      ["Switch the ignition OFF.",
       "Press and hold the ODO/TRIP button on the instrument cluster.",
       "While still holding the button, switch the ignition ON. Do not start the engine.",
       "Keep holding until the display counts down and shows 000000, then release.",
       "Switch the ignition OFF and ON again to confirm the reminder has cleared."],
      before="Make sure the cluster is showing the odometer (not Trip A or B) before you start."),
    P("mazda-connect-service", "Mazda – scheduled maintenance via Mazda Connect",
      ["Open the Settings menu on the centre display with the commander knob.",
       "Select Vehicle, then Maintenance.",
       "Select Scheduled (or Oil Change for diesel engines).",
       "Select Reset and confirm with Yes.",
       "Check the maintenance message no longer shows in the cluster."],
      before="Ignition ON, vehicle stationary."),
    P("hyundai-kia-service", "Hyundai / Kia – service interval via cluster user settings",
      ["With the vehicle stopped, press the cluster mode button on the steering wheel until User Settings appears.",
       "Select Service Interval (sometimes under Convenience or Vehicle).",
       "Select Reset.",
       "Confirm with OK, then check the next interval shows the full distance and time."],
      before="Ignition ON, gear in P (or neutral with park brake on)."),
    P("ford-display-service", "Ford – oil life reset via information display",
      ["Use the left steering-wheel arrow buttons to open Settings on the information display.",
       "Select Vehicle, then Oil Life.",
       "Press and hold OK until the display confirms the oil life has been reset to 100%.",
       "Switch the ignition OFF and ON again and check the oil life reads 100%."],
      before="Ignition ON, engine off."),
    P("mitsubishi-info-service", "Mitsubishi – service reminder via INFO switch",
      ["Press the INFO switch on the cluster repeatedly until the service reminder (spanner) screen shows.",
       "Press and hold the INFO switch for about 2 seconds until the spanner symbol flashes.",
       "Press the INFO switch once more while it flashes.",
       "The display shows CLEAR and then the next service interval."],
      before="Ignition ON, engine off."),
    P("vw-infotainment-service", "Volkswagen – oil change service via infotainment",
      ["Press the CAR button (or Vehicle on newer screens) on the infotainment unit.",
       "Select Setup (or Settings), then Service & Check.",
       "Select Reset oil change service.",
       "Confirm Reset. Check the days and distance to the next oil change are shown."],
      before="Ignition ON, engine off.",
      notes="This resets the oil change interval only. The inspection interval is reset separately with a scan tool."),
    P("nissan-display-service", "Nissan – maintenance reminder via vehicle information display",
      ["Use the steering-wheel buttons to open Settings on the vehicle information display.",
       "Select Maintenance, then Engine Oil.",
       "Select Reset and press OK.",
       "Repeat for Oil Filter if it is listed separately."],
      before="Ignition ON, vehicle stationary."),

    P("toyota-tpms-mid", "Toyota – TPMS initialise via multi-info display",
      ["Set all four tyres to the placard pressure (tyres cold). The spare is not monitored on most models.",
       "Ignition ON. On the multi-information display, open Settings (gear), then Vehicle Settings, then TPMS.",
       "Select Set Pressure and press and hold OK until the tyre pressure warning light blinks 3 times.",
       "Wait a few minutes with the ignition ON. A message confirms initialisation has started.",
       "Drive at 40 km/h or more for 10–30 minutes to complete initialisation."],
      before="Tyres cold, vehicle stationary.",
      notes="New or rotated sensors may need their IDs registered with a TPMS tool.",
      tpms="Direct"),
    P("mazda-tpms-indirect", "Mazda – TPMS initialise (indirect)",
      ["Set all four tyres to the placard pressure (tyres cold).",
       "Ignition ON. On Mazda Connect open Settings, then Vehicle, then Tire Pressure Monitor.",
       "Select Initialize and confirm Yes.",
       "Wait for the TPMS light in the cluster to flash twice and a confirmation chime.",
       "Drive normally. The system learns the new tyre pressures over the next drive."],
      before="Tyres cold, vehicle stationary.",
      notes="Repeat whenever tyre pressures are changed or tyres are rotated or replaced.",
      tpms="Indirect"),
    P("hyundai-kia-tpms-auto", "Hyundai / Kia – TPMS relearn (automatic)",
      ["Set all four tyres to the placard pressure (tyres cold).",
       "There is no reset button. Drive at more than 25 km/h for about 10 minutes.",
       "Check the TPMS warning light has gone out and the cluster shows all four pressures."],
      before="Tyres cold.",
      notes="If a wheel or sensor was replaced, the new sensor ID must be written with a TPMS tool.",
      tpms="Direct"),
    P("vw-tpms-indirect", "Volkswagen – tyre pressure loss indicator (indirect)",
      ["Set all four tyres to the placard pressure (tyres cold).",
       "Ignition ON. Press CAR on the infotainment, then Setup, then Tyres.",
       "Select SET and confirm. The cluster stores the new reference.",
       "Drive normally. The system finishes learning after some distance at varying speeds."],
      before="Tyres cold, vehicle stationary.",
      tpms="Indirect"),
]

def V(id, make, model, series, yfrom, yto, engine, code, fuel, cap, grade, spec, ryco, ftype, svc,
      tpms=None, cap_note="With filter", grade_note=None, notes=None):
    oil = dict(capacityLitres=cap, capacityNote=cap_note, grade=grade, spec=spec)
    if grade_note: oil["gradeNote"] = grade_note
    v = dict(id=id, make=make, model=model, series=series, yearFrom=yfrom, yearTo=yto, engine=engine,
             engineCode=code, fuel=fuel, oil=oil, oilFilter=dict(ryco=ryco, type=ftype),
             serviceResetId=svc, tpmsResetId=tpms, verified=False, notes=notes)
    return {k: val for k, val in v.items() if val is not None}

vehicles = [
    V("toyota-hilux-an120-1gd", "Toyota", "Hilux", "AN120/AN130", 2015, None, "2.8L turbo diesel", "1GD-FTV", "Diesel",
      7.5, "5W-30", "ACEA C2 / JASO DL-1", "R2735P", "Cartridge", "toyota-mid-service",
      notes="Early grades without a multi-info display use the ODO/TRIP button method."),
    V("toyota-hilux-an120-2gd", "Toyota", "Hilux", "AN120/AN130", 2015, None, "2.4L turbo diesel", "2GD-FTV", "Diesel",
      7.5, "5W-30", "ACEA C2 / JASO DL-1", "R2735P", "Cartridge", "toyota-mid-service"),
    V("toyota-prado-150-1gd", "Toyota", "LandCruiser Prado", "GDJ150", 2015, 2024, "2.8L turbo diesel", "1GD-FTV", "Diesel",
      7.5, "5W-30", "ACEA C2 / JASO DL-1", "R2735P", "Cartridge", "toyota-mid-service", tpms="toyota-tpms-mid"),
    V("toyota-corolla-e210-m20a", "Toyota", "Corolla", "MZEA12", 2018, None, "2.0L petrol", "M20A-FKS", "Petrol",
      4.4, "0W-16", "API SP / ILSAC GF-6B", "Z436", "SpinOn", "toyota-mid-service", tpms="toyota-tpms-mid",
      grade_note="0W-20 (API SP / ILSAC GF-6A) may be listed as an alternative."),
    V("toyota-corolla-e170-2zr", "Toyota", "Corolla", "ZRE172", 2013, 2018, "1.8L petrol", "2ZR-FE", "Petrol",
      4.2, "0W-20", "API SN / ILSAC GF-5", "Z386", "SpinOn", "toyota-odo-service"),
    V("toyota-rav4-xa50-a25a", "Toyota", "RAV4", "AXAA54", 2019, None, "2.5L petrol", "A25A-FKS", "Petrol",
      4.5, "0W-16", "API SP / ILSAC GF-6B", "Z436", "SpinOn", "toyota-mid-service", tpms="toyota-tpms-mid"),
    V("toyota-rav4-xa50-hybrid", "Toyota", "RAV4 Hybrid", "AXAH54", 2019, None, "2.5L petrol hybrid", "A25A-FXS", "Hybrid",
      4.5, "0W-16", "API SP / ILSAC GF-6B", "Z436", "SpinOn", "toyota-mid-service", tpms="toyota-tpms-mid"),
    V("ford-ranger-px-p5at", "Ford", "Ranger", "PX", 2011, 2022, "3.2L 5-cyl turbo diesel", "P5AT", "Diesel",
      9.2, "5W-30", "Ford WSS-M2C913-C / ACEA A5/B5", "R2701P", "Cartridge", "ford-display-service",
      notes="PX1 (2011–2015) uses a different cluster; the reset menu path may differ."),
    V("mazda-cx5-kf-py", "Mazda", "CX-5", "KF", 2017, None, "2.5L petrol", "PY-VPS", "Petrol",
      4.5, "0W-20", "API SP / ILSAC GF-6A", "Z663", "SpinOn", "mazda-connect-service", tpms="mazda-tpms-indirect"),
    V("mazda-3-bp-pe", "Mazda", "Mazda3", "BP", 2019, None, "2.0L petrol", "PE-VPS", "Petrol",
      4.2, "0W-20", "API SP / ILSAC GF-6A", "Z663", "SpinOn", "mazda-connect-service", tpms="mazda-tpms-indirect"),
    V("hyundai-i30-pd-nu20", "Hyundai", "i30", "PD", 2017, 2020, "2.0L petrol", "G4NC", "Petrol",
      4.0, "5W-30", "API SN / ILSAC GF-5", "Z436", "SpinOn", "hyundai-kia-service", tpms="hyundai-kia-tpms-auto"),
    V("kia-cerato-bd-nu20", "Kia", "Cerato", "BD", 2018, 2024, "2.0L petrol", "G4NH", "Petrol",
      4.0, "5W-30", "API SP / ILSAC GF-6A", "Z436", "SpinOn", "hyundai-kia-service",
      notes="Sample record has no TPMS. Higher grades may have it: check for a TPMS light in the cluster."),
    V("kia-sportage-nq5-20d", "Kia", "Sportage", "NQ5", 2022, None, "2.0L turbo diesel", "D4HE", "Diesel",
      6.6, "0W-30", "ACEA C2", "R2756P", "Cartridge", "hyundai-kia-service", tpms="hyundai-kia-tpms-auto"),
    V("mitsubishi-triton-mq-4n15", "Mitsubishi", "Triton", "MQ/MR", 2015, 2024, "2.4L turbo diesel", "4N15", "Diesel",
      8.4, "5W-30", "ACEA C2", "Z1141", "SpinOn", "mitsubishi-info-service"),
    V("vw-golf-mk7-cjz", "Volkswagen", "Golf", "Mk7 (AU)", 2013, 2020, "1.4L turbo petrol", "CZDA", "Petrol",
      4.0, "5W-30", "VW 502 00", "Z1093", "SpinOn", "vw-infotainment-service", tpms="vw-tpms-indirect"),
    V("nissan-navara-d23-ys23", "Nissan", "Navara", "D23 (NP300)", 2015, None, "2.3L twin-turbo diesel", "YS23DDTT", "Diesel",
      6.9, "5W-30", "ACEA C3", "Z1097", "SpinOn", "nissan-display-service"),
]

dataset = dict(
    info=dict(
        name="Built-in SAMPLE data (not verified)",
        verified=False,
        updated="2026-10-26",
        notes=("Demonstration values only. They have NOT been checked against Ryco, Penrite, Valvoline "
               "or vehicle-maker data and may be wrong. Confirm every value in the official lookup "
               "before working on a car, or import a workshop-checked data file."),
    ),
    procedures=procedures,
    vehicles=vehicles,
)

out = pathlib.Path(__file__).resolve().parents[1] / "src/main/assets/vehicles.json"
out.write_text(json.dumps(dataset, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {out} ({len(vehicles)} vehicles, {len(procedures)} procedures)")
