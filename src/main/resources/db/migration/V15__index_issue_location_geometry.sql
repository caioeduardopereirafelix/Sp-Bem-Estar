CREATE INDEX idx_issue_location_geometry ON issue USING GIST ((location::geometry));
