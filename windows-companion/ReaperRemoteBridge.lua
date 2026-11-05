-- REAPER Remote persistent bridge (Lua).
-- Install via Actions > Show action list > ReaScript: Load and run.
-- Companion server must be running on 127.0.0.1:8765.
-- Uses REAPER's built-in Lua + ReaScript API; no external Lua modules required.

local resource = reaper.GetResourcePath()
local bridge_dir = resource .. "/Scripts/REAPER Remote"
local state_path = bridge_dir .. "/reaper_state.json"
local command_path = bridge_dir .. "/commands.json"
local ack_path = bridge_dir .. "/bridge_ack.json"
local http_base = "http://127.0.0.1:8765"

-- The companion and script exchange small JSON files in the REAPER resource folder.
-- The Python companion mirrors its command queue/state into this folder in the next
-- integration step. This script is intentionally conservative: no project is closed
-- without an explicit phone command.

local function json_escape(s)
  s = tostring(s or "")
  s = s:gsub("\\", "\\\\"):gsub('"', '\\"'):gsub("\n", "\\n"):gsub("\r", "\\r")
  return s
end

local function project_snapshot()
  local projects = {}
  local active = reaper.EnumProjects(-1, "")
  local i = 0
  while true do
    local proj, filename = reaper.EnumProjects(i, "")
    if not proj then break end
    local _, title = reaper.GetProjectName(proj, "")
    local _, path = reaper.GetProjectPathEx(proj, "")
    local is_active = (proj == active)
    projects[#projects + 1] = string.format(
      '{"index":%d,"title":"%s","file":"%s","path":"%s","active":%s,"dirty":%s}',
      i, json_escape(title), json_escape(filename), json_escape(path),
      tostring(is_active), tostring(reaper.GetProjectStateChangeCount(proj) > 0)
    )
    i = i + 1
  end
  local play = reaper.GetPlayState()
  local transport = play == 0 and "stopped" or (play == 1 and "playing" or (play == 2 and "paused" or (play == 5 and "recording" or "other")))
  local pos = reaper.GetPlayPosition()
  local _, active_name = reaper.GetProjectName(active, "")
  return string.format(
    '{"connected":true,"activeProject":"%s","transport":{"state":"%s","position":%.3f},"projects":[%s],"updated":%.0f}',
    json_escape(active_name), transport, pos, table.concat(projects, ","), os.time()
  )
end

local function read_commands()
  local f = io.open(command_path, "r")
  if not f then return {} end
  local s = f:read("*a"); f:close()
  -- JSON parsing is deliberately handled by the companion in this prototype.
  return s
end

local function run_action(name, args)
  args = args or {}
  if name == "play" then reaper.OnPlayButton()
  elseif name == "pause" then reaper.OnPauseButton()
  elseif name == "stop" then reaper.OnStopButton()
  elseif name == "record" then reaper.On録Button()
  elseif name == "next_marker" then reaper.Main_OnCommand(40173, 0)
  elseif name == "prev_marker" then reaper.Main_OnCommand(40172, 0)
  elseif name == "project_open" and args.path then reaper.Main_openProject(args.path)
  elseif name == "project_activate" and args.index then
    local proj = reaper.EnumProjects(tonumber(args.index), "")
    if proj then reaper.SelectProjectInstance(proj) end
  elseif name == "project_close" and args.index then
    local proj = reaper.EnumProjects(tonumber(args.index), "")
    if proj then
      reaper.SelectProjectInstance(proj)
      reaper.Main_OnCommand(40860, 0) -- File: Close project
    end
  elseif name == "render_start" then
    reaper.Main_OnCommand(41824, 0) -- Render project using most recent settings
  end
end

local function loop()
  -- State publishing and command transport are wired by the companion's bridge sync endpoint.
  -- Keep a state snapshot in the REAPER resource directory for diagnostics.
  reaper.RecursiveCreateDirectory(bridge_dir, 0)
  local f = io.open(state_path, "w")
  if f then f:write(project_snapshot()); f:close() end
  reaper.defer(loop)
end

loop()
