import request from '@/utils/request'

export function add(data) {
  return request({
    url: 'api/${changeClassName}',
    method: 'post',
    data
  })
}

export function del(ids) {
  return request({
    url: 'api/${changeClassName}/',
    method: 'delete',
    data: ids
  })
}

export function edit(data) {
  return request({
    url: 'api/${changeClassName}',
    method: 'put',
    data
  })
}

export function downloadExcel() {
return request({
url: 'api/${changeClassName}/downloadExcel',
method: 'get',
responseType: 'blob'
})
}

export function importExcelFile(data) {
return request({
url: 'api/${changeClassName}/importExcel',
method: 'post',
data
})
}
export default { add, edit, del, downloadExcel, importExcelFile }
