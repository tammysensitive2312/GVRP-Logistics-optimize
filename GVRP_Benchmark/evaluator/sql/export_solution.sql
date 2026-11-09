-- Read-only export of one stored solution and the exact inputs of its job, as one JSON document.
-- Usage: prepend `SET @solution_id = <id>;` (see export_solution.sh). Run as agent_reader.
-- Every lookup is scoped to the solution's branch. No customer name, phone or address is selected.
SELECT JSON_OBJECT(
  'solution', (SELECT JSON_OBJECT(
      'id', s.id, 'job_id', s.job_id, 'branch_id', s.branch_id, 'status', s.status, 'type', s.type,
      'total_cost', s.total_cost, 'total_distance', s.total_distance, 'total_co2', s.total_co2,
      'total_time', s.total_time, 'total_vehicles_used', s.total_vehicles_used,
      'served_orders', s.served_orders, 'unserved_orders', s.unserved_orders)
    FROM solutions s WHERE s.id = @solution_id),
  'job_input', (SELECT j.input_data FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id
    WHERE s.id = @solution_id AND j.branch_id = s.branch_id),
  'orders', (SELECT JSON_ARRAYAGG(JSON_OBJECT(
      'id', o.id, 'lat', ST_Latitude(o.location), 'lng', ST_Longitude(o.location),
      'demand', o.demand, 'service_time', o.service_time,
      'tw_start', TIME_FORMAT(o.time_window_start, '%H:%i:%s'),
      'tw_end', TIME_FORMAT(o.time_window_end, '%H:%i:%s'),
      'required_skills', o.required_skills))
    FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id
    JOIN JSON_TABLE(j.input_data->'$.order_ids', '$[*]' COLUMNS (oid BIGINT PATH '$')) t
    JOIN orders o ON o.id = t.oid AND o.branch_id = s.branch_id
    WHERE s.id = @solution_id),
  'vehicles', (SELECT JSON_ARRAYAGG(JSON_OBJECT(
      'id', v.id, 'vehicle_type_id', v.vehicle_type_id,
      'start_depot_id', v.start_depot_id, 'end_depot_id', v.end_depot_id))
    FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id
    JOIN JSON_TABLE(j.input_data->'$.vehicle_ids', '$[*]' COLUMNS (vid BIGINT PATH '$')) t
    JOIN vehicles v ON v.id = t.vid
    JOIN fleets f ON f.id = v.fleet_id AND f.branch_id = s.branch_id
    WHERE s.id = @solution_id),
  'vehicle_types', (SELECT JSON_ARRAYAGG(JSON_OBJECT(
      'id', vt.id, 'capacity', vt.capacity, 'fixed_cost', vt.fixed_cost, 'cost_per_km', vt.cost_per_km,
      'cost_per_hour', vt.cost_per_hour, 'max_distance', vt.max_distance, 'max_duration', vt.max_duration,
      'vehicle_features', vt.vehicle_features))
    FROM vehicle_types vt
    WHERE vt.branch_id = (SELECT branch_id FROM solutions WHERE id = @solution_id)
      AND vt.id IN (SELECT v.vehicle_type_id FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id
        JOIN JSON_TABLE(j.input_data->'$.vehicle_ids', '$[*]' COLUMNS (vid BIGINT PATH '$')) t
        JOIN vehicles v ON v.id = t.vid WHERE s.id = @solution_id)),
  'depots', (SELECT JSON_ARRAYAGG(JSON_OBJECT(
      'id', d.id, 'lat', ST_Latitude(d.location), 'lng', ST_Longitude(d.location)))
    FROM depots d
    WHERE d.branch_id = (SELECT branch_id FROM solutions WHERE id = @solution_id)
      AND d.id IN (SELECT v.start_depot_id FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id
          JOIN JSON_TABLE(j.input_data->'$.vehicle_ids', '$[*]' COLUMNS (vid BIGINT PATH '$')) t
          JOIN vehicles v ON v.id = t.vid WHERE s.id = @solution_id
        UNION SELECT v.end_depot_id FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id
          JOIN JSON_TABLE(j.input_data->'$.vehicle_ids', '$[*]' COLUMNS (vid BIGINT PATH '$')) t
          JOIN vehicles v ON v.id = t.vid WHERE s.id = @solution_id)),
  'routes', (SELECT JSON_ARRAYAGG(JSON_OBJECT(
      'id', r.id, 'vehicle_id', r.vehicle_id, 'route_order', r.route_order,
      'stops', (SELECT JSON_ARRAYAGG(JSON_OBJECT(
          'type', rs.type, 'order_id', rs.order_id, 'sequence_number', rs.sequence_number,
          'arrival_time', TIME_FORMAT(rs.arrival_time, '%H:%i:%s'),
          'departure_time', TIME_FORMAT(rs.departure_time, '%H:%i:%s'),
          'distance_to_next', rs.distance_to_next, 'time_to_next', rs.time_to_next))
        FROM route_stops rs WHERE rs.route_id = r.id)))
    FROM routes r WHERE r.solution_id = @solution_id),
  'unassigned', (SELECT JSON_ARRAYAGG(u.order_id) FROM unassigned_orders u WHERE u.solution_id = @solution_id),
  -- Jobs store ids only; rows edited after the job ran make the bundle differ from the solved input.
  'job_created_at', (SELECT DATE_FORMAT(j.created_at, '%Y-%m-%dT%H:%i:%s') FROM solutions s
    JOIN optimization_jobs j ON j.id = s.job_id WHERE s.id = @solution_id),
  'inputs_updated_after_job', (SELECT JSON_OBJECT(
      'vehicle_types', (SELECT JSON_ARRAYAGG(vt.id) FROM vehicle_types vt
        WHERE vt.branch_id = s.branch_id AND vt.updated_at > j.created_at AND vt.id IN (
          SELECT v.vehicle_type_id FROM JSON_TABLE(j.input_data->'$.vehicle_ids', '$[*]' COLUMNS (vid BIGINT PATH '$')) t
          JOIN vehicles v ON v.id = t.vid)),
      'vehicles', (SELECT COUNT(*) FROM JSON_TABLE(j.input_data->'$.vehicle_ids', '$[*]' COLUMNS (vid BIGINT PATH '$')) t
        JOIN vehicles v ON v.id = t.vid WHERE v.updated_at > j.created_at),
      'orders', (SELECT COUNT(*) FROM JSON_TABLE(j.input_data->'$.order_ids', '$[*]' COLUMNS (oid BIGINT PATH '$')) t
        JOIN orders o ON o.id = t.oid AND o.branch_id = s.branch_id WHERE o.updated_at > j.created_at))
    FROM solutions s JOIN optimization_jobs j ON j.id = s.job_id WHERE s.id = @solution_id)
) AS export;
