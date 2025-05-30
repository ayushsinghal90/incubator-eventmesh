// Code generated by MockGen. DO NOT EDIT.
// Source: github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/core/protocol/grpc/emitter (interfaces: EventEmitter)

// Package mocks is a generated GoMock package.
package mocks

import (
	reflect "reflect"

	grpc "github.com/apache/incubator-eventmesh/eventmesh-server-go/pkg/common/protocol/grpc"
	pb "github.com/apache/incubator-eventmesh/eventmesh-server-go/runtime/proto/pb"
	gomock "github.com/golang/mock/gomock"
)

// MockEventEmitter is a mock of EventEmitter interface.
type MockEventEmitter struct {
	ctrl     *gomock.Controller
	recorder *MockEventEmitterMockRecorder
}

// MockEventEmitterMockRecorder is the mock recorder for MockEventEmitter.
type MockEventEmitterMockRecorder struct {
	mock *MockEventEmitter
}

// NewMockEventEmitter creates a new mock instance.
func NewMockEventEmitter(ctrl *gomock.Controller) *MockEventEmitter {
	mock := &MockEventEmitter{ctrl: ctrl}
	mock.recorder = &MockEventEmitterMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockEventEmitter) EXPECT() *MockEventEmitterMockRecorder {
	return m.recorder
}

// SendStreamResp mocks base method.
func (m *MockEventEmitter) SendStreamResp(arg0 *pb.RequestHeader, arg1 *grpc.StatusCode) error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "SendStreamResp", arg0, arg1)
	ret0, _ := ret[0].(error)
	return ret0
}

// SendStreamResp indicates an expected call of SendStreamResp.
func (mr *MockEventEmitterMockRecorder) SendStreamResp(arg0, arg1 interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "SendStreamResp", reflect.TypeOf((*MockEventEmitter)(nil).SendStreamResp), arg0, arg1)
}
