import {reducer as notifications} from "react-notification-system-redux"
import {combineReducers} from "redux"
import {reducerWithUndo as graphReducer} from "./graph"
import {reducer as httpErrorHandler} from "./httpErrorHandler"
import {reducer as processActivity} from "./processActivity"
import {reducer as settings} from "./settings"
import {toolbars} from "./toolbars"
import {reducer as nodeDetails} from "./nodeDetailsState"
import {reducer as ui} from "./ui"
import {featureFlags} from "./featureFlags"
import {userSettings} from "./userSettings"

export const reducer = combineReducers({
  httpErrorHandler,
  graphReducer,
  settings,
  ui,
  processActivity,
  notifications,
  toolbars,
  featureFlags,
  userSettings,
  nodeDetails,
})

export type RootState = ReturnType<typeof reducer>

export default reducer
