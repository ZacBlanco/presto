use crate::protocol::resources::{
    Duration, MemoryPoolId, NodeStatus, ServerInfo, TaskId, TaskUpdateRequest,
};
use crate::resources::{AppState, MemoryPoolAssignmentsRequest, TaskManager};
use actix_web::{delete, get, head, post, put, web, Error, HttpResponse};
use log::{debug, error};
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

pub fn worker_config(config: &mut web::ServiceConfig) {
    config
        .service(post_memory)
        .service(get_memory_pool)
        .service(get_status)
        .service(get_info)
        .service(get_state)
        .service(put_state)
        .service(get_coordinator)
        .service(get_task_info)
        .service(get_task_status)
        .service(post_task)
        .service(delete_task);
}

type Response = Result<HttpResponse, Error>;

/// Memory

#[post("/memory")]
async fn post_memory(
    state: web::Data<AppState>,
    _request: web::Json<MemoryPoolAssignmentsRequest>,
) -> Response {
    Ok(HttpResponse::Ok().json(state.memory_manager.lock().await.get_info().await))
}

#[get("/memory/{pool_id}")]
async fn get_memory_pool(state: web::Data<AppState>, path: web::Path<(String,)>) -> Response {
    let args = path.into_inner();
    let info = state
        .memory_manager
        .lock()
        .await
        .get_pool_info(&MemoryPoolId(args.0))
        .await;
    Ok(HttpResponse::Ok().json(info))
}

/// Server Info

#[get("/info")]
async fn get_info(state: web::Data<AppState>) -> Response {
    let info: ServerInfo = ServerInfo {
        coordinator: false,
        environment: state.node_info.environment.clone(),
        nodeVersion: state.node_version.clone(),
        uptime: SystemTime::now()
            .duration_since(
                UNIX_EPOCH + std::time::Duration::from_millis(state.node_info.start_time as u64),
            )
            .ok()
            .map(|x| Duration(x.as_millis())),
        starting: false,
    };
    Ok(HttpResponse::Ok().json(info))
}

#[get("/info/state")]
async fn get_state(state: web::Data<AppState>) -> Response {
    let body;
    {
        body = state.get_ref().node_state.lock().await.clone()
    }
    Ok(HttpResponse::Ok().json(body))
}

#[put("/info/state")]
async fn put_state() -> Response {
    Ok(HttpResponse::NotImplemented().finish())
}

#[put("/info/coordinator")]
async fn get_coordinator() -> Response {
    // coordinator not supported on oxidized worker
    Ok(HttpResponse::NotFound().finish())
}

/// Status

#[get("/status")]
async fn get_status(state: web::Data<AppState>) -> Response {
    Ok(HttpResponse::Ok().json(TryInto::<NodeStatus>::try_into(state.get_ref())?))
}

#[head("/status")]
async fn status_ping() -> Response {
    Ok(HttpResponse::Ok().finish())
}

/// Control Plane Operations

#[derive(Debug, Serialize, Deserialize)]
struct TaskRequestParameters {
    summarize: Option<String>,
    abort: Option<String>,
}

#[post("/task/{task_id}")]
async fn post_task(
    state: web::Data<AppState>,
    task_id: web::Path<(TaskId,)>,
    request: web::Bytes,
    params: web::Query<TaskRequestParameters>,
) -> Response {
    let rq = serde_json::from_slice::<TaskUpdateRequest>(&request);
    if rq.is_err() {
        let e = rq.unwrap_err();
        error!("Failed to deserialize request: {:?}", e);
        debug!("{}", String::from_utf8(request.to_vec()).unwrap());
        return Ok(HttpResponse::InternalServerError()
            .body(format!("failed to deserialize json response {:?}", e)));
    } else {
        debug!("Successfully parsed TaskUpdateRequest!");
    }
    let info = state
        .task_manager
        .update_task(&task_id.0, rq.unwrap(), params.summarize.is_some());
    Ok(HttpResponse::Ok().json(info))
}

#[get("/task/{task_id}/status")]
async fn get_task_status(state: web::Data<AppState>, task_id: web::Path<(TaskId,)>) -> Response {
    let status = state.task_manager.get_task_status(&task_id.0);
    Ok(HttpResponse::Ok().json(status))
}

#[get("/task/{task_id}")]
async fn get_task_info(
    state: web::Data<AppState>,
    task_id: web::Path<(TaskId,)>,
    params: web::Query<TaskRequestParameters>,
) -> Response {
    let task_info = state
        .task_manager
        .get_task_info(&task_id.0, params.summarize.is_some());
    Ok(HttpResponse::Ok().json(task_info))
}

#[delete("/task/{task_id}")]
async fn delete_task(
    state: web::Data<AppState>,
    task_id: web::Path<(TaskId,)>,
    params: web::Query<TaskRequestParameters>,
) -> Response {
    let summ = params.summarize.is_some();
    let task_info = match params.abort.is_some() {
        true => state.task_manager.cancel_task(&task_id.0, summ),
        false => state.task_manager.abort_task(&task_id.0, summ),
    };
    Ok(HttpResponse::Ok().json(task_info))
}

#[get("/task")]
async fn get_tasks(
    state: web::Data<AppState>,
    params: web::Query<TaskRequestParameters>,
) -> Response {
    let task_infos = state
        .task_manager
        .get_all_task_info(params.summarize.is_some());
    Ok(HttpResponse::Ok().json(task_infos))
}

/// Data Plane Operations

#[get("/task/async/{task_id}/results/{buffer_id}/{token}")]
async fn get_buffer_token() -> Response {
    Ok(HttpResponse::NotImplemented().finish())
}

#[get("/task/async/{task_id}/results/{buffer_id}/{token}/acknowledge")]
async fn get_buffer_token_ack() -> Response {
    Ok(HttpResponse::NotImplemented().finish())
}

#[delete("/task/async/{task_id}/results/{buffer_id}/{token}")]
async fn delete_buffer_token() -> Response {
    Ok(HttpResponse::NotImplemented().finish())
}
