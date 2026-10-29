/**
 * Google encoded-polyline decoder (precision 1e5), no dependency.
 *
 * The Entry API stores leg geometries in this format (`RouteGeometryDTO.Leg.polyline`),
 * with points in `[lat, lon]` order. Returns `[lat, lng]` pairs to match Leaflet.
 */
export function decodePolyline(encoded: string): [number, number][] {
  const points: [number, number][] = [];
  if (!encoded) {
    return points;
  }

  let index = 0;
  let latitude = 0;
  let longitude = 0;

  while (index < encoded.length) {
    let shift = 0;
    let result = 0;
    let byte: number;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20 && index < encoded.length);
    latitude += (result & 1) ? ~(result >> 1) : (result >> 1);

    shift = 0;
    result = 0;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20 && index < encoded.length);
    longitude += (result & 1) ? ~(result >> 1) : (result >> 1);

    points.push([latitude / 1e5, longitude / 1e5]);
  }

  return points;
}
