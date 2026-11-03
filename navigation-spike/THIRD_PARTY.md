# Third-party notices

This spike uses Ferrostar 0.59.0 (BSD-3-Clause). Its Android Auto wiring and navigation configuration are adapted from the Ferrostar demo. The recorded test response `src/androidTest/assets/valhalla-route.json` comes from Ferrostar's `common/ferrostar/src/fixtures/valhalla_osrm_response.json` in tag 0.59.0.

Upstream: https://github.com/stadiamaps/ferrostar/tree/0.59.0
See `LICENSE-Ferrostar.txt` for the upstream license.

MapLibre renders OpenFreeMap basemaps derived from OpenStreetMap / OpenMapTiles. Attribution is shown in the phone and car UI. Ferrostar's transitive libraries retain their own licenses.

The debug-only driving replay uses `valhalla_extended_osrm_response.json` from the same upstream tag, saved as `src/debug/assets/valhalla-driving-route.json`. The guidance/speech test fixture is a separate upstream walking route.
