import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
// 로고 이미지가 있으면 아래 경로를 맞게 수정하세요
// import logoImg from '../assets/logo.png';

export function LoginPage() {
  const navigate = useNavigate();
  const [isSignup, setIsSignup] = useState(false);
  const [formData, setFormData] = useState({
    id: '',
    password: '',
    passwordConfirm: ''
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    navigate('/dashboard');
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="flex w-full max-w-5xl bg-gray-50">
        {/* Left: Brand Area */}
        <div className="flex-1 bg-gradient-to-br from-blue-50 to-white flex items-center justify-center p-12">
          <div className="text-center max-w-md">
            <div className="border-2 border-blue-200 rounded-2xl px-16 py-12 mb-8 bg-white">
              <div className="flex flex-col items-center mb-6">
                {/* 로고 이미지가 있으면 아래 주석을 해제하고 위 import도 활성화하세요 */}
                {/* <img src={logoImg} alt="NimbusTech" className="w-20 h-20 mb-4 object-contain" /> */}
                <div className="w-20 h-20 mb-4 bg-blue-100 rounded-full flex items-center justify-center text-3xl">
                  ☁️
                </div>
                <h1 className="text-3xl text-gray-800 font-medium">NimbusTech</h1>
              </div>
              <p className="text-xl text-gray-600">AI 영업 자동화 에이전트</p>
            </div>
            <p className="text-gray-500">
              AI 기반 영업 활동 분석 및<br />
              의사결정 지원 시스템
            </p>
          </div>
        </div>

        {/* Right: Login Form */}
        <div className="flex-1 bg-white flex items-center justify-center p-12">
          <div className="w-full max-w-md">
            <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-8">
              <h2 className="text-xl text-gray-800 mb-8 text-center">
                {isSignup ? '회원가입' : '로그인'}
              </h2>

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm text-gray-700 mb-2">아이디</label>
                  <input
                    type="text"
                    value={formData.id}
                    onChange={(e) => setFormData({ ...formData, id: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
                    placeholder="아이디를 입력하세요"
                  />
                </div>

                <div>
                  <label className="block text-sm text-gray-700 mb-2">비밀번호</label>
                  <input
                    type="password"
                    value={formData.password}
                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
                    placeholder="비밀번호를 입력하세요"
                  />
                </div>

                {isSignup && (
                  <div>
                    <label className="block text-sm text-gray-700 mb-2">비밀번호 확인</label>
                    <input
                      type="password"
                      value={formData.passwordConfirm}
                      onChange={(e) => setFormData({ ...formData, passwordConfirm: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-blue-500 text-sm"
                      placeholder="비밀번호를 다시 입력하세요"
                    />
                  </div>
                )}

                <button
                  type="submit"
                  className="w-full bg-blue-500 hover:bg-blue-600 text-white py-2.5 rounded text-sm"
                >
                  {isSignup ? '회원가입' : '로그인'}
                </button>
              </form>

              <div className="mt-6 text-center">
                <button
                  onClick={() => setIsSignup(!isSignup)}
                  className="text-xs text-gray-600 hover:text-gray-800"
                >
                  {isSignup ? '이미 계정이 있으신가요? 로그인' : '계정이 없으신가요? 회원가입'}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
