import request from '@/utils/request'
export const getImgVerifyCode = () => request.get('/front/resource/img_verify_code')
export const uploadImage = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/front/resource/image', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
